package dev.zeann3th.stresspilot.core.services.flows.nodes;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;

import java.util.Map;

public interface FlowNodeHandler {

    FlowStepType getSupportedType();

    String handle(FlowStepEntity step, Map<String, FlowStepEntity> stepMap, FlowExecutionContext context);
}
