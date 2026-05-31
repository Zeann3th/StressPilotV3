package dev.zeann3th.stresspilot.core.services.flows.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DefaultFlowExecutor extends FlowExecutor {

    @Override
    public String getType() {
        return FlowType.DEFAULT.name();
    }

    @Override
    public boolean supports(String type) {
        return FlowType.DEFAULT.name().equals(type);
    }

    public void executeAssignedWorkers(FlowExecutionContext baseContext) {
        Map<String, FlowStepEntity> stepMap = baseContext.getSteps().stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));
        executeWorkers(baseContext, stepMap, baseContext.getCommand().getThreads());
    }

    @Override
    protected void executeWorker(FlowExecutionContext ctx, Map<String, FlowStepEntity> stepMap) {
        FlowStepEntity startStep = findStartNode(stepMap);
        if (startStep == null) return;

        log.info("Run {} thread {} started", ctx.getRunId(), ctx.getThreadId());

        while (!ctx.shouldStop()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Thread {} iteration error: {}", ctx.getThreadId(), e.getMessage(), e);
            }
        }

        log.info("Run {} thread {} finished: {} iterations", ctx.getRunId(), ctx.getThreadId(), ctx.getIterationCount());
        ctx.getExecutionContext().clear();
    }

}
