package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
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

    public abstract String getType();

    public abstract boolean supports(String type);

    /**
     * Template method — orchestrates the full run lifecycle.
     * Plugin makers do not override this; they implement {@link #executeWorker} instead.
     */
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

        int threads = Math.max(1, cmd.getThreads());
        long totalMs = (long) cmd.getTotalDuration() * 1000;
        long rampUpMs = (long) cmd.getRampUpDuration() * 1000;
        long rampDelay = threads > 1 ? rampUpMs / (threads - 1) : 0;
        long deadline = System.currentTimeMillis() + totalMs;

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

    /**
     * Executes the work for a single virtual thread.
     * Plugin makers implement this method — the infrastructure (run save, project lookup,
     * env merge, register/deregister, finalize) is handled by the template above.
     */
    protected abstract void executeWorker(int threadId, RunEntity run,
            Map<String, FlowStepEntity> stepMap,
            Map<String, Object> environment,
            long totalMs, AtomicBoolean stopSignal);

    /**
     * Called by {@link FlowExecutorFactory} to inject infrastructure dependencies
     * into plugin-loaded extensions that are not managed by Spring.
     */
    public void initInfra(ProjectStore ps, EnvironmentVariableStore evs, RunStore rs,
            ActiveRunRegistry arr, RequestLogService rls) {
        this.projectStore = ps;
        this.envVarStore = evs;
        this.runStore = rs;
        this.activeRunRegistry = arr;
        this.requestLogService = rls;
    }
}