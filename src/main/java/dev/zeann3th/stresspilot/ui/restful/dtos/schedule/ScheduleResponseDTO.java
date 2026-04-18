package dev.zeann3th.stresspilot.ui.restful.dtos.schedule;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduleResponseDTO {
    private Long id;
    private Long flowId;
    private String quartzExpr;
    private Boolean enabled;
    private Integer threads;
    private Integer duration;
    private Integer rampUp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
