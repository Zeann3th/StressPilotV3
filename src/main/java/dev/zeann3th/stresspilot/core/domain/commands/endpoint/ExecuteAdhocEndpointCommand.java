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
public class ExecuteAdhocEndpointCommand {
    private Long projectId;
    private String type;
    private String url;
    private Object body;
    private String httpMethod;
    private Map<String, Object> httpHeaders;
    private Map<String, Object> httpParameters;
    private String grpcServiceName;
    private String grpcMethodName;
    private String grpcStubPath;
    private String successCondition;
    private Map<String, Object> variables;
}
