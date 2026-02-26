package dev.zeann3th.stresspilot.core.domain.commands.endpoint;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecuteAdhocEndpointCommand extends ExecuteEndpointCommand{
    private String type;
}
