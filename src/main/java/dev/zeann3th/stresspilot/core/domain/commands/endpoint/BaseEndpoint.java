package dev.zeann3th.stresspilot.core.domain.commands.endpoint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class BaseEndpoint {
    private String name;
    private String description;
    private String type;
    private String url;
    private Object body;
    private String successCondition;
    // HTTP
    private String httpMethod;
    private Map<String, Object> httpHeaders;
    private Map<String, Object> httpParameters;
    // gRPC
    private String grpcServiceName;
    private String grpcMethodName;
    private String grpcStubPath;
    private Long projectId;
}
