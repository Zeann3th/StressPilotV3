package dev.zeann3th.stresspilot.ui.restful.dtos.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectRequestDTO {
    @NotBlank
    private String name;
    private String description;
}
