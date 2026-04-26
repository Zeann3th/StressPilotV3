package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.ExtensionPoint;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public abstract class FlowExecutor implements ExtensionPoint {

    @Autowired protected ActiveRunRegistry activeRunRegistry;
    @Autowired protected FlowNodeHandlerFactory nodeHandlerFactory;

    public abstract String getType();

    public abstract boolean supports(String type);

    protected int maxThreads() {
        return Integer.MAX_VALUE;
    }

    public final String execute(FlowExecutionData data) {
        String runId = data.getRunId();
        RunEntity run = data.getRun();
        RunFlowCommand cmd = data.getCommand();

        Map<String, FlowStepEntity> stepMap = data.getSteps().stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));

        AtomicBoolean stopSignal = activeRunRegistry.registerRun(runId);

        log.info("Run {} started: flow={}, threads={}, duration={}s, rampUp={}s",
                runId, run.getFlow().getName(), cmd.getThreads(),
                cmd.getTotalDuration(), cmd.getRampUpDuration());

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

                Map<String, Object> threadEnv = new java.util.HashMap<>(data.getBaseEnvironment());
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
        }

        boolean durationMet = System.currentTimeMillis() >= deadline;
        return (stopSignal.get() && !durationMet)
                ? RunStatus.ABORTED.name()
                : RunStatus.COMPLETED.name();
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

    public void initInfra(ActiveRunRegistry arr, FlowNodeHandlerFactory nhf) {
        this.activeRunRegistry = arr;
        this.nodeHandlerFactory = nhf;
    }
}
