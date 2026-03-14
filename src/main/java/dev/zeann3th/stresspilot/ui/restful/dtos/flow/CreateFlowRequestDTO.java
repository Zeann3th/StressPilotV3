package dev.zeann3th.stresspilot.ui.restful.dtos.flow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateFlowRequestDTO {
    @NotNull
    private Long projectId;
    @NotBlank
    private String name;
    private String description;
    private String type;
}
