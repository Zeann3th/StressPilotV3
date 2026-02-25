package dev.zeann3th.stresspilot.ui.restful.dtos.flows;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFlowRequestDTO {
    @NotNull(message = "Project ID must not be null")
    private Long projectId;
    @NotBlank(message = "Flow name must not be blank")
    private String name;
    private String description;
}
