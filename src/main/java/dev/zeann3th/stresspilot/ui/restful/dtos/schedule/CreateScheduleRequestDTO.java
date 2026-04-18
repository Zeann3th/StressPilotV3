package dev.zeann3th.stresspilot.ui.restful.dtos.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateScheduleRequestDTO {
    @NotNull
    private Long flowId;
    @NotBlank
    private String quartzExpr;
    private Integer threads = 1;
    private Integer duration = 60;
    private Integer rampUp = 0;
    private Boolean enabled = true;
}
