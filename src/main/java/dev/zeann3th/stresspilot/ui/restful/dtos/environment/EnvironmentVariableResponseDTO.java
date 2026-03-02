package dev.zeann3th.stresspilot.ui.restful.dtos.environment;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnvironmentVariableResponseDTO extends BaseDTO {
    private Long id;
    private String key;
    private String value;
    private boolean active;
}
