package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Command to create a new flow under a project. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFlowCommand {
    private Long projectId;
    private String name;
    private String description;
}
