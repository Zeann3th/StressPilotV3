package dev.zeann3th.stresspilot.core.services.flows.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class DefaultFlowExecutor extends FlowExecutor {

    @Autowired
    private FlowNodeHandlerFactory nodeHandlerFactory;

    @Override
    public String getType() {
        return FlowType.DEFAULT.name();
    }

    @Override
    public boolean supports(String type) {
        return FlowType.DEFAULT.name().equals(type);
    }

    @Override
    @SuppressWarnings("java:S3776")
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

        log.info("Run {} thread {} started", run.getId(), threadId);

        while (!stopSignal.get() && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Thread {} iteration error: {}", threadId, e.getMessage(), e);
            }
        }

        log.info("Run {} thread {} finished: {} iterations", run.getId(), threadId, ctx.getIterationCount());
        ctx.getExecutionContext().clear();
    }

    private void executeIteration(FlowStepEntity startStep,
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

            FlowStepType type;
            try {
                type = FlowStepType.valueOf(current.getType().toUpperCase());
            } catch (IllegalArgumentException _) {
                log.error("Unknown step type: {}", current.getType());
                break;
            }

            String nextId = nodeHandlerFactory.getHandler(type).handle(current, stepMap, ctx);
            current = nextId != null ? stepMap.get(nextId) : null;
        }
    }

    private FlowStepEntity findStartNode(Map<String, FlowStepEntity> stepMap) {
        return stepMap.values().stream()
                .filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType()))
                .findFirst()
                .orElse(null);
    }
}