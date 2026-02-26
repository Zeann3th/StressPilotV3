package dev.zeann3th.stresspilot.core.domain.commands.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointStats {
    private String endpointName;
    private Long endpointId;
    private Integer requests;
    private Double avgMs;
    private Double p90Ms;
    private Double p95Ms;
}
