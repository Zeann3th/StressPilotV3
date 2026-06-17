package dev.zeann3th.stresspilot.core.utils.report;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ElementRenderContext {
    private final RunReport report;
    private final List<RequestLog> logs;
    private final List<ReportTimeBucket> timeBuckets;
    private final CustomReportElementEntity element;

    // endpointStats convenience — same as report.getEndpointStats() but never null
    public List<EndpointStats> getEndpointStats() {
        if (report == null || report.getEndpointStats() == null) return List.of();
        return report.getEndpointStats();
    }
}
