package dev.zeann3th.stresspilot.ui.restful.dtos.project;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectResponseDTO extends BaseDTO {
    private Long id;
    private String name;
    private String description;
    private Long environmentId;
}
