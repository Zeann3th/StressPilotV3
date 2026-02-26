package dev.zeann3th.stresspilot.ui.restful.dtos.environment;

import lombok.Data;

@Data
public class EnvironmentVariableResponseDTO {
    private Long id;
    private String key;
    private String value;
    private boolean active;
}
