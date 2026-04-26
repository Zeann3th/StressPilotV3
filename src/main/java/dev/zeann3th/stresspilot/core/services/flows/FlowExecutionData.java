package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class FlowExecutionData {
    private String runId;
    private RunEntity run;
    private String flowType;
    private List<FlowStepEntity> steps;
    private Map<String, Object> baseEnvironment;
    private RunFlowCommand command;
}
