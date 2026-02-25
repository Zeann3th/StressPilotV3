package dev.zeann3th.stresspilot.ui.restful.dtos.flows;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunResponseDTO {
    private Long id;
    private Long flowId;
    private String status;
    private Integer threads;
    private Integer duration;
    private Integer rampUpDuration;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
