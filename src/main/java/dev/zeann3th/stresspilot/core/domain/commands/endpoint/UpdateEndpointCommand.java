package dev.zeann3th.stresspilot.core.domain.commands.endpoint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEndpointCommand {
    private Long id;
    private Map<String, Object> updates;
}
