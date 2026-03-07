package dev.zeann3th.stresspilot.core.domain.commands.project;

import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportProjectCommand {

    private String name;
    private String description;
    private List<EnvVar> environment;
    private List<Endpoint> endpoints;
    private List<Flow> flows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvVar {
        private String name;
        private String value;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Endpoint {
        private String id;
        private String name;
        private String description;
        private String type;
        private String url;
        private String method;
        private Map<String, Object> headers;
        private Map<String, Object> parameters;
        private Object body;
        private String successCondition;
        private String grpcServiceName;
        private String grpcMethodName;
        private String grpcStubPath;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Flow {
        private String name;
        private String description;
        private List<Step> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private String name;
        private String type;
        private String endpoint;
        private String condition;
        private String nextIfTrue;
        private String nextIfFalse;
        private Map<String, Object> preProcess;
        private Map<String, Object> postProcess;
    }
}
