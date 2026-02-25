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
public class ExecuteEndpointCommand {
    private Long endpointId;
    private String url;
    private Object body;
    private Map<String, Object> httpHeaders;
    private Map<String, Object> httpParameters;
    private Map<String, Object> variables;
}
