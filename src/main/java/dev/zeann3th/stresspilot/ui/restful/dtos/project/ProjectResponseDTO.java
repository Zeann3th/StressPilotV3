package dev.zeann3th.stresspilot.ui.restful.dtos.project;

import lombok.Data;

@Data
public class ProjectResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long environmentId;
}
