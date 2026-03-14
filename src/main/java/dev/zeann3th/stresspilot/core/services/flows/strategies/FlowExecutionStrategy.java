package dev.zeann3th.stresspilot.core.services.flows.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import org.pf4j.ExtensionPoint;

import java.util.List;

public interface FlowExecutionStrategy extends ExtensionPoint {
    boolean supports(String type);

    void execute(FlowEntity flow, List<FlowStepEntity> steps, RunFlowCommand runFlowCommand);
}
