package dev.zeann3th.stresspilot.ui.restful.dtos.config;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDTO {
    @NotBlank
    private String key;

    @NotBlank
    private String value;
}
