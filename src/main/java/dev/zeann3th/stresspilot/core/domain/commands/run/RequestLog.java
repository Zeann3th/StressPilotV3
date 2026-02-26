package dev.zeann3th.stresspilot.core.domain.commands.run;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {
    private Long id;
    private Long endpointId;
    private Integer statusCode;
    private Boolean success;
    private Long responseTime;
    private String request;
    private String response;
    private LocalDateTime createdAt;
}
