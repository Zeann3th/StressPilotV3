package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunFlowCommand {
    private Long flowId;
    private int threads;
    private int totalDuration;
    private int rampUpDuration;
    private Map<String, Object> variables;
    private List<Map<String, Object>> credentials;
}
