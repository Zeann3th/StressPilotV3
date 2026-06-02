package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import lombok.Data;

import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;

import java.util.List;
import java.util.Map;

@Data
public class DryRunStepResponseDTO {
    private String stepId;
    private String stepType;
    private String nextStepId;
    private String correlationId;
    private boolean persisted;
    private Object outputData;
    private Map<String, Object> variables;
    private List<RequestLog> requestLogs;
}
