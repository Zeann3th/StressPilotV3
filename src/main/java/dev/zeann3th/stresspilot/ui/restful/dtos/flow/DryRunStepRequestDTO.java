package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import lombok.Data;

import java.util.Map;

@Data
public class DryRunStepRequestDTO {
    private String stepId;
    private Long environmentId;
    private Map<String, Object> variables;
    private Map<String, Object> temporaryVariables;
}
