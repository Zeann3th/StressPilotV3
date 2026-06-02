package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunStepCommand {
    private String stepId;
    private Long environmentId;
    private Map<String, Object> variables;
    private Map<String, Object> temporaryVariables;
}
