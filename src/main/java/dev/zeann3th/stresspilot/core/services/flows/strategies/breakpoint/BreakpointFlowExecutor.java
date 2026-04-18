package dev.zeann3th.stresspilot.core.services.flows.strategies.breakpoint;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class BreakpointFlowExecutor extends FlowExecutor {

    private static final double DEFAULT_THRESHOLD = 0.5;

    @Autowired private BreakpointRegistry breakpointRegistry;
    @Autowired private ConfigService configService;

    @Override
    public String getType() {
        return FlowType.BREAKPOINT.name();
    }

    @Override
    public boolean supports(String type) {
        return FlowType.BREAKPOINT.name().equals(type);
    }

    @Override
    protected void beforeWorkers(String runId, RunFlowCommand cmd) {
        double threshold = configService.getValue(ConfigKey.BREAKPOINT_ERROR_THRESHOLD.name())
                .map(v -> {
                    try { return Double.parseDouble(v); }
                    catch (NumberFormatException _) { return DEFAULT_THRESHOLD; }
                })
                .orElse(DEFAULT_THRESHOLD);
        breakpointRegistry.register(runId, threshold);
        log.info("Breakpoint run {} registered with error threshold={}", runId, threshold);
    }

    @Override
    protected void afterWorkers(String runId) {
        breakpointRegistry.deregister(runId);
    }

    @Override
    protected void executeWorker(int threadId, RunEntity run,
            Map<String, FlowStepEntity> stepMap,
            Map<String, Object> environment,
            long totalMs, AtomicBoolean stopSignal) {
        long deadline = System.currentTimeMillis() + totalMs;

        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setThreadId(threadId);
        ctx.setRunId(run.getId());
        ctx.setRun(run);
        ctx.setVariables(new ConcurrentHashMap<>(environment));
        ctx.setExecutionContext(new BaseExecutionContext());
        ctx.setStopSignal(stopSignal);
        ctx.setDeadline(deadline);

        FlowStepEntity startStep = findStartNode(stepMap);
        if (startStep == null) return;

        log.info("Breakpoint Run {} thread {} started", run.getId(), threadId);

        long lastTotal = 0;
        long lastFailed = 0;

        while (!stopSignal.get() && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Breakpoint thread {} iteration error: {}", threadId, e.getMessage(), e);
            }

            long currentTotal = ctx.getRequestCount();
            long currentFailed = ctx.getFailureCount();
            long deltaTotal = currentTotal - lastTotal;
            long deltaFailed = currentFailed - lastFailed;
            lastTotal = currentTotal;
            lastFailed = currentFailed;

            if (deltaTotal > 0) {
                breakpointRegistry.record(run.getId(), deltaTotal, deltaFailed);
                if (breakpointRegistry.isBreached(run.getId())) {
                    log.warn("Breakpoint Run {} thread {}: error rate exceeded threshold — stopping run", run.getId(), threadId);
                    stopSignal.set(true);
                    break;
                }
            }
        }

        log.info("Breakpoint Run {} thread {} finished: {} iterations", run.getId(), threadId, ctx.getIterationCount());
        ctx.getExecutionContext().clear();
    }
}
