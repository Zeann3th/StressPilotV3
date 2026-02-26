package dev.zeann3th.stresspilot.ui.restful.dtos.endpoint;

import dev.zeann3th.stresspilot.ui.restful.validators.ValidEndpointRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
@ValidEndpointRequest
public class CreateEndpointRequestDTO {
    @NotNull
    private Long projectId;
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String type;
    @NotBlank
    private String url;
    private Object body;
    private String successCondition;
    private String httpMethod;
    private Map<String, String> httpHeaders;
    private Map<String, String> httpParameters;
    private String grpcServiceName;
    private String grpcMethodName;
    private String grpcStubPath;
}
