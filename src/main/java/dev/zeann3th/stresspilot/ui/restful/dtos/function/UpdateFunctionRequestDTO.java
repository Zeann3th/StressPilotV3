package dev.zeann3th.stresspilot.ui.restful.dtos.function;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFunctionRequestDTO {
    private String name;
    private String body;
    private String description;
}
