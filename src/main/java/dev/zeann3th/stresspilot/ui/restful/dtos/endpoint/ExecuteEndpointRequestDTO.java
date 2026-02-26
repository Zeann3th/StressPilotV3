package dev.zeann3th.stresspilot.ui.restful.dtos.endpoint;

import lombok.Data;
import java.util.Map;

@Data
public class ExecuteEndpointRequestDTO {
    private String url;
    private Map<String, Object> variables;
    private Object body;
    private String successCondition;
    private String httpMethod;
    private Map<String, String> httpHeaders;
    private Map<String, String> httpParameters;
    private String grpcServiceName;
    private String grpcMethodName;
    private String grpcStubPath;
}
