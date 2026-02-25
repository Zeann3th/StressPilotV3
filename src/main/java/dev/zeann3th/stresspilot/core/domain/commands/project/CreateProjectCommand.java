package dev.zeann3th.stresspilot.core.domain.commands.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProjectCommand {
    private String name;
    private String description;
}
