package dev.zeann3th.stresspilot.core.domain.commands.project;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectImportExport {
    private String name;
    private String description;
    private List<EnvironmentVariable> environmentVariables;
    private List<Endpoint> endpoints;
    private List<Flow> flows;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnvironmentVariable {
        private Long id;
        private Long environmentId;
        private String key;
        private String value;
        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Endpoint {
        private Long id; // old ID for mapping
        private String name;
        private String description;
        private String type;
        private String url;
        private Object body;
        private String successCondition;
        private String httpMethod;
        private Map<String, Object> httpHeaders;
        private Map<String, Object> httpParameters;
        private String grpcServiceName;
        private String grpcMethodName;
        private String grpcStubPath;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Flow {
        private String name;
        private String description;
        private List<FlowStep> steps;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FlowStep {
        private String id;
        private String type;
        private Long endpointId;
        private Map<String, Object> preProcessor;
        private Map<String, Object> postProcessor;
        private String nextIfTrue;
        private String nextIfFalse;
        private String condition;
    }
}
