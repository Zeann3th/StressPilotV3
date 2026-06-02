package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunStepResult {
    private String stepId;
    private String stepType;
    private String nextStepId;
    private String correlationId;
    private boolean persisted;
    private Object outputData;
    private Map<String, Object> variables;
    private List<RequestLog> requestLogs;
}
