package dev.zeann3th.stresspilot.ui.restful.dtos.projects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectRequestDTO {
    private String name;
    private String description;
}
