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
public class RunReport {
    private Long runId;
    private Integer totalRequests;
    private Integer successCount;
    private Integer failureCount;
    private Double successRate;
    private Double failureRate;
    private Double avgResponse;
    private Double p90;
    private Double p95;
    private Double durationSeconds;
    private Double tps;
    private Integer ccus;
    private Integer rampUpTime;
    private Integer configuredDuration;
    private List<EndpointStats> endpointStats;
    private List<RequestLog> details;
}
