package dev.zeann3th.stresspilot.ui.restful.dtos.project;

import lombok.Data;

@Data
public class UpdateProjectRequestDTO {
    private String name;
    private String description;
}
