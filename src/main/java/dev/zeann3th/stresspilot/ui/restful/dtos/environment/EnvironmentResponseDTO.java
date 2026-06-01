package dev.zeann3th.stresspilot.ui.restful.dtos.environment;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnvironmentResponseDTO extends BaseDTO {
    private Long id;
    private String name;
    private Long projectId;
}
