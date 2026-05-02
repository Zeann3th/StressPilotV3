package dev.zeann3th.stresspilot.core.services.flows.nodes;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import org.pf4j.ExtensionPoint;

import java.util.Map;

public interface FlowNodeHandler extends ExtensionPoint {

    String getSupportedType();

    NodeHandlerResult handle(FlowStepEntity step, Map<String, FlowStepEntity> stepMap, FlowExecutionContext context);
}
