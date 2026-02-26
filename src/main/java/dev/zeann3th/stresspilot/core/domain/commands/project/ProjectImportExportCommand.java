package dev.zeann3th.stresspilot.core.domain.commands.project;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Serialized form of a full project snapshot for import/export.
 * Used as both the payload class (import) and the output value object (export).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectImportExportCommand {

    private Long id;
    private String name;
    private String description;
    private List<EnvVarData> environmentVariables;
    private List<EndpointData> endpoints;
    private List<FlowData> flows;

    // ─── Nested types ─────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvVarData {
        private Long id;
        private String key;
        private String value;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointData {
        /** Original DB id — used for remapping during import so flow steps can reference them. */
        private Long oldId;
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
    public static class FlowData {
        private Long id;
        private String name;
        private String description;
        private List<StepData> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepData {
        private String id;
        private String type;
        private Long oldEndpointId;
        private Map<String, Object> preProcessor;
        private Map<String, Object> postProcessor;
        private String nextIfTrue;
        private String nextIfFalse;
        private String condition;
    }
}
