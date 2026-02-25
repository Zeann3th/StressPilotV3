package dev.zeann3th.stresspilot.core.domain.commands.flow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFlowCommand {
    private Long id;
    private Map<String, Object> updates;
}
