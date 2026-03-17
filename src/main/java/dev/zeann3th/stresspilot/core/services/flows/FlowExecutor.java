package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import org.pf4j.ExtensionPoint;

import java.util.List;

public interface FlowExecutor extends ExtensionPoint {
    String getType();

    boolean supports(String type);

    void execute(FlowEntity flow, List<FlowStepEntity> steps, RunFlowCommand runFlowCommand);
}
