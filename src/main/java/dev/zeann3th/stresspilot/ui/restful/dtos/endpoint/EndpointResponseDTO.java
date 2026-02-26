package dev.zeann3th.stresspilot.ui.restful.dtos.endpoint;

import lombok.Data;

/** Response DTO returned to the UI layer for endpoint operations. */
@Data
public class EndpointResponseDTO {
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
