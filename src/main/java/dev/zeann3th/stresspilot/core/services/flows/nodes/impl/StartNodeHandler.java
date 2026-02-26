package dev.zeann3th.stresspilot.core.services.flows.nodes.impl;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Handles the START node: simply forwards execution to {@code nextIfTrue}.
 */
@Component
public class StartNodeHandler implements FlowNodeHandler {

    @Override
    public FlowStepType getSupportedType() {
        return FlowStepType.START;
    }

    @Override
    public String handle(FlowStepEntity step, Map<String, FlowStepEntity> stepMap, FlowExecutionContext context) {
        return step.getNextIfTrue();
    }
}
