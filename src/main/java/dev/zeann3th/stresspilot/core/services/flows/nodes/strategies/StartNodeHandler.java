package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.services.flows.nodes.NodeHandlerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StartNodeHandler implements FlowNodeHandler {

    @Override
    public String getSupportedType() {
        return FlowStepType.START.name();
    }

    @Override
    public NodeHandlerResult handle(FlowStepEntity step, Map<String, FlowStepEntity> stepMap, FlowExecutionContext context) {
        return NodeHandlerResult.of(step.getNextIfTrue());
    }
}
