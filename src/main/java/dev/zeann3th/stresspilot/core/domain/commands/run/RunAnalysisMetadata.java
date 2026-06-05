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
public class RunAnalysisMetadata {
    private String id;
    private Long flowId;
    private String status;
    private Integer threads;
    private Integer duration;
    private Integer loopCount;
    private Integer rampUpDuration;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
