package dev.zeann3th.stresspilot.core.domain.commands.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLogResponse {
    private Long id;
    private Long endpointId;
    private Integer statusCode;
    private Long responseTime;
    private String request;
    private String response;
    private LocalDateTime createdAt;
}
