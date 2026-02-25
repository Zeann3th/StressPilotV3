package dev.zeann3th.stresspilot.ui.restful.dtos.endpoints;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExecuteEndpointRequestDTO {
    private String url;
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();
    private Object body;
    private String successCondition;

    private String httpMethod;
    @Builder.Default
    private Map<String, Object> httpHeaders = new HashMap<>();
    @Builder.Default
    private Map<String, Object> httpParameters = new HashMap<>();

    private String grpcServiceName;
    private String grpcMethodName;
    private String grpcStubPath;
}
