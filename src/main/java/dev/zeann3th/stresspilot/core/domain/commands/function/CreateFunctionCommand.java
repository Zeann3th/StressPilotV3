package dev.zeann3th.stresspilot.core.domain.commands.function;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFunctionCommand {
    private String name;
    private String body;
    private String description;
}
