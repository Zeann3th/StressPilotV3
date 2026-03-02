package dev.zeann3th.stresspilot.ui.restful.dtos.endpoint;

import dev.zeann3th.stresspilot.ui.restful.dtos.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EndpointResponseDTO extends BaseDTO {
    private Long id;
    private String name;
    private String description;
    private String type;
    private String url;
    private String body;
    private String successCondition;
    private String httpMethod;
    private String httpHeaders;
    private String httpParameters;
    private String grpcServiceName;
    private String grpcMethodName;
    private String grpcStubPath;
    private Long projectId;
}
