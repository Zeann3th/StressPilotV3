package dev.zeann3th.stresspilot.ui.restful.dtos.function;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFunctionRequestDTO {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Body is required")
    private String body;

    private String description;
}
