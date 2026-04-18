package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.ExtensionPoint;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowExecutor implements ExtensionPoint {

    @Autowired protected ProjectStore projectStore;
    @Autowired protected EnvironmentVariableStore envVarStore;
    @Autowired protected RunStore runStore;
    @Autowired protected ActiveRunRegistry activeRunRegistry;
    @Autowired protected RequestLogService requestLogService;
    @Autowired protected FlowNodeHandlerFactory nodeHandlerFactory;

    public abstract String getType();

    public abstract boolean supports(String type);

    protected int maxThreads() {
        return Integer.MAX_VALUE;
    }

    public final String execute(String runId, FlowEntity flow, List<FlowStepEntity> steps, RunFlowCommand cmd) {
        Map<String, FlowStepEntity> stepMap = steps.stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));

        RunEntity run = runStore.save(RunEntity.builder()
                .id(runId)
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(cmd.getThreads())
                .duration(cmd.getTotalDuration())
                .rampUpDuration(cmd.getRampUpDuration())
                .startedAt(LocalDateTime.now())
                .build());

        AtomicBoolean stopSignal = activeRunRegistry.registerRun(runId);

        log.info("Run {} started: flow={}, threads={}, duration={}s, rampUp={}s",
                runId, flow.getName(), cmd.getThreads(),
                cmd.getTotalDuration(), cmd.getRampUpDuration());

        ProjectEntity project = projectStore.findById(flow.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        Map<String, Object> baseEnv = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(project.getEnvironmentId())
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (_, v2) -> v2, HashMap::new));
        if (cmd.getVariables() != null) {
            baseEnv.putAll(cmd.getVariables());
        }

        int threads = Math.clamp(cmd.getThreads(), 1, maxThreads());
        long totalMs = (long) cmd.getTotalDuration() * 1000;
        long rampUpMs = (long) cmd.getRampUpDuration() * 1000;
        long rampDelay = threads > 1 ? rampUpMs / (threads - 1) : 0;
        long deadline = System.currentTimeMillis() + totalMs;

        beforeWorkers(runId, cmd);

        try (ExecutorService pool = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("sp-worker-" + runId + "-", 0).factory())) {

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int threadId = i;
                final long initialDelayMs = i * rampDelay;

                Map<String, Object> threadEnv = new HashMap<>(baseEnv);
                if (cmd.getCredentials() != null && !cmd.getCredentials().isEmpty()) {
                    threadEnv.putAll(cmd.getCredentials().get(i % cmd.getCredentials().size()));
                }

                futures.add(pool.submit(() -> {
                    if (initialDelayMs > 0) {
                        try {
                            Thread.sleep(initialDelayMs);
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    executeWorker(threadId, run, stepMap, threadEnv, totalMs, stopSignal);
                }));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("Worker thread encountered error: {}", e.getMessage());
                }
            }

        } finally {
            afterWorkers(runId);
            activeRunRegistry.deregisterRun(runId);

            boolean durationMet = System.currentTimeMillis() >= deadline;
            String finalStatus = (stopSignal.get() && !durationMet)
                    ? RunStatus.ABORTED.name()
                    : RunStatus.COMPLETED.name();

            int updated = runStore.finalizeRun(runId, finalStatus, LocalDateTime.now());
            if (updated == 0) {
                log.info("Run {} already finalized externally (likely ABORTED)", runId);
            }

            requestLogService.ensureFlushed();
            log.info("Run {} finished: status={}", runId, finalStatus);
        }

        return runId;
    }

    protected void beforeWorkers(String runId, RunFlowCommand cmd) { }

    protected void afterWorkers(String runId) { }

    protected abstract void executeWorker(int threadId, RunEntity run,
            Map<String, FlowStepEntity> stepMap,
            Map<String, Object> environment,
            long totalMs, AtomicBoolean stopSignal);

    protected final void executeIteration(FlowStepEntity startStep,
            Map<String, FlowStepEntity> stepMap,
            FlowExecutionContext ctx) {
        FlowStepEntity current = startStep;
        int jumpCount = 0;
        final int MAX_JUMPS = 10000;

        while (current != null) {
            if (ctx.shouldStop()) break;

            if (jumpCount++ > MAX_JUMPS) {
                log.warn("Iteration exceeded max jumps ({}) at step {} — breaking iteration", MAX_JUMPS, current.getId());
                break;
            }

            String type = current.getType().toUpperCase();

            String nextId = nodeHandlerFactory.getHandler(type).handle(current, stepMap, ctx);
            current = nextId != null ? stepMap.get(nextId) : null;
        }
    }

    protected final FlowStepEntity findStartNode(Map<String, FlowStepEntity> stepMap) {
        return stepMap.values().stream()
                .filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType()))
                .findFirst()
                .orElse(null);
    }

    public void initInfra(ProjectStore ps, EnvironmentVariableStore evs, RunStore rs,
            ActiveRunRegistry arr, RequestLogService rls, FlowNodeHandlerFactory nhf) {
        this.projectStore = ps;
        this.envVarStore = evs;
        this.runStore = rs;
        this.activeRunRegistry = arr;
        this.requestLogService = rls;
        this.nodeHandlerFactory = nhf;
    }
}
