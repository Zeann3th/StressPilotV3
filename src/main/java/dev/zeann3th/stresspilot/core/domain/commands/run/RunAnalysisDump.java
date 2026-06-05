package dev.zeann3th.stresspilot.core.domain.commands.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunAnalysisDump {
    private RunAnalysisMetadata run;
    private RunReport report;
    private Integer logCount;
    private List<RequestLog> logs;
}
