package dev.zeann3th.stresspilot.ui.restful.dtos.projects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long environmentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
