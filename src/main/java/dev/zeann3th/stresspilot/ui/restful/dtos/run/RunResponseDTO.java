package dev.zeann3th.stresspilot.ui.restful.dtos.run;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RunResponseDTO {
    private String id;
    private Long flowId;
    private String status;
    private Integer threads;
    private Integer duration;
    private Integer rampUpDuration;
    private String metricsEndpoint;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
