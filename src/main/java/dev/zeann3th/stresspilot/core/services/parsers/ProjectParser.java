package dev.zeann3th.stresspilot.core.services.parsers;

import dev.zeann3th.stresspilot.core.domain.commands.project.ImportProjectCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandException;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

@Component
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class ProjectParser {

    private static final String ROOT_KEY = Constants.APP_NAME;
    private static final String EXTRACT = "extract";
    private static final String INJECT = "inject";
    private static final String DELAY = "delay";
    private static final String DESCRIPTION = "description";
    private static final String VALUE = "value";

    public ImportProjectCommand unmarshal(String spec) {
        Map<String, Object> root = parseRoot(spec);

        Map<String, Object> project = getMap(root, "project");
        List<Map<String, Object>> envNodes = getList(root, "environment");
        List<Map<String, Object>> endpointNodes = getList(root, "endpoints");
        List<Map<String, Object>> flowNodes = getList(root, "flows");

        List<ImportProjectCommand.EnvVar> envVars = new ArrayList<>();
        for (Map<String, Object> env : envNodes) {
            envVars.add(new ImportProjectCommand.EnvVar(
                    getString(env, "name"),
                    getString(env, VALUE),
                    getBoolean(env, "active", true)
            ));
        }

        List<ImportProjectCommand.Endpoint> endpoints = new ArrayList<>();
        for (Map<String, Object> ep : endpointNodes) {
            endpoints.add(buildEndpoint(ep));
        }

        List<ImportProjectCommand.Flow> flows = new ArrayList<>();
        for (Map<String, Object> flowNode : flowNodes) {
            flows.add(buildFlow(flowNode));
        }

        return ImportProjectCommand.builder()
                .name(getString(project, "name"))
                .description(getString(project, DESCRIPTION))
                .environment(envVars)
                .endpoints(endpoints)
                .flows(flows)
                .build();
    }

    public String marshal(ImportProjectCommand command) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> stresspilot = new LinkedHashMap<>();

        Map<String, Object> project = new LinkedHashMap<>();
        project.put("name", command.getName());
        if (command.getDescription() != null) {
            project.put(DESCRIPTION, command.getDescription());
        }
        stresspilot.put("project", project);

        if (command.getEnvironment() != null && !command.getEnvironment().isEmpty()) {
            List<Map<String, Object>> envList = new ArrayList<>();
            for (ImportProjectCommand.EnvVar v : command.getEnvironment()) {
                Map<String, Object> envMap = new LinkedHashMap<>();
                envMap.put("name", v.getName());
                envMap.put(VALUE, v.getValue());
                if (v.getActive() != null && !v.getActive()) {
                    envMap.put("active", false);
                }
                envList.add(envMap);
            }
            stresspilot.put("environment", envList);
        }

        if (command.getEndpoints() != null && !command.getEndpoints().isEmpty()) {
            List<Map<String, Object>> epList = new ArrayList<>();
            for (ImportProjectCommand.Endpoint ep : command.getEndpoints()) {
                epList.add(marshalEndpoint(ep));
            }
            stresspilot.put("endpoints", epList);
        }

        if (command.getFlows() != null && !command.getFlows().isEmpty()) {
            List<Map<String, Object>> flowList = new ArrayList<>();
            for (ImportProjectCommand.Flow flow : command.getFlows()) {
                flowList.add(marshalFlow(flow));
            }
            stresspilot.put("flows", flowList);
        }

        root.put(ROOT_KEY, stresspilot);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        return yaml.dump(root);
    }

    // --- Unmarshal helpers ---

    protected Map<String, Object> parseRoot(String spec) {
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> document = yaml.load(spec);
            Object root = document.get(ROOT_KEY);
            if (root instanceof Map<?, ?> rootMap) {
                return (Map<String, Object>) rootMap;
            }
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Invalid StressPilot YAML: missing '" + ROOT_KEY + "' root object"));
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Error parsing StressPilot YAML: " + e.getMessage()));
        }
    }

    private ImportProjectCommand.Endpoint buildEndpoint(Map<String, Object> ep) {
        return ImportProjectCommand.Endpoint.builder()
                .id(getString(ep, "id"))
                .name(getString(ep, "name"))
                .description(getString(ep, DESCRIPTION))
                .type(getString(ep, "type", "HTTP").toUpperCase())
                .url(getString(ep, "url"))
                .method(getString(ep, "method"))
                .headers(getMap(ep, "headers"))
                .parameters(getMap(ep, "parameters"))
                .body(ep.get("body"))
                .successCondition(getString(ep, "success_condition"))
                .grpcServiceName(getString(ep, "grpc_service_name"))
                .grpcMethodName(getString(ep, "grpc_method_name"))
                .grpcStubPath(getString(ep, "grpc_stub_path"))
                .build();
    }

    private ImportProjectCommand.Flow buildFlow(Map<String, Object> flowNode) {
        List<Map<String, Object>> stepNodes = getList(flowNode, "steps");
        List<ImportProjectCommand.Step> steps = new ArrayList<>();
        for (Map<String, Object> stepNode : stepNodes) {
            steps.add(buildStep(stepNode));
        }
        return new ImportProjectCommand.Flow(getString(flowNode, "name"), getString(flowNode, DESCRIPTION), steps);
    }

    private ImportProjectCommand.Step buildStep(Map<String, Object> stepNode) {
        Map<String, Object> preProcess = buildProcessor(getMap(stepNode, "pre_process"));
        Map<String, Object> postProcess = buildProcessor(getMap(stepNode, "post_process"));

        return ImportProjectCommand.Step.builder()
                .name(getString(stepNode, "name"))
                .type(getString(stepNode, "type", "ENDPOINT").toUpperCase())
                .endpoint(getString(stepNode, "endpoint"))
                .condition(getString(stepNode, "condition"))
                .nextIfTrue(getString(stepNode, "next_if_true"))
                .nextIfFalse(getString(stepNode, "next_if_false"))
                .preProcess(preProcess.isEmpty() ? null : preProcess)
                .postProcess(postProcess.isEmpty() ? null : postProcess)
                .build();
    }

    protected Map<String, Object> buildProcessor(Map<String, Object> processNode) {
        if (processNode == null || processNode.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> processor = new LinkedHashMap<>();
        collectNameValueList(processNode, INJECT, processor);
        collectNameValueList(processNode, EXTRACT, processor);

        Object delay = processNode.get(DELAY);
        if (delay != null) {
            processor.put(DELAY, delay);
        }

        return processor;
    }

    private void collectNameValueList(Map<String, Object> source, String key, Map<String, Object> target) {
        List<Map<String, Object>> items = getList(source, key);
        if (items.isEmpty()) return;

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String name = getString(item, "name");
            if (name != null) {
                result.put(name, item.get(VALUE));
            }
        }
        if (!result.isEmpty()) {
            target.put(key, result);
        }
    }

    private Map<String, Object> marshalEndpoint(ImportProjectCommand.Endpoint ep) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, "id", ep.getId());
        putIfNotNull(map, "name", ep.getName());
        putIfNotNull(map, DESCRIPTION, ep.getDescription());
        putIfNotNull(map, "type", ep.getType());
        putIfNotNull(map, "url", ep.getUrl());
        putIfNotNull(map, "method", ep.getMethod());
        putIfNotEmpty(map, "headers", ep.getHeaders());
        putIfNotEmpty(map, "parameters", ep.getParameters());
        if (ep.getBody() != null) {
            map.put("body", ep.getBody());
        }
        putIfNotNull(map, "success_condition", ep.getSuccessCondition());
        putIfNotNull(map, "grpc_service_name", ep.getGrpcServiceName());
        putIfNotNull(map, "grpc_method_name", ep.getGrpcMethodName());
        putIfNotNull(map, "grpc_stub_path", ep.getGrpcStubPath());
        return map;
    }

    private Map<String, Object> marshalFlow(ImportProjectCommand.Flow flow) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", flow.getName());
        putIfNotNull(map, DESCRIPTION, flow.getDescription());

        if (flow.getSteps() != null && !flow.getSteps().isEmpty()) {
            List<Map<String, Object>> stepList = new ArrayList<>();
            for (ImportProjectCommand.Step step : flow.getSteps()) {
                stepList.add(marshalStep(step));
            }
            map.put("steps", stepList);
        }
        return map;
    }

    private Map<String, Object> marshalStep(ImportProjectCommand.Step step) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, "name", step.getName());
        putIfNotNull(map, "type", step.getType());
        putIfNotNull(map, "endpoint", step.getEndpoint());
        putIfNotNull(map, "condition", step.getCondition());
        putIfNotNull(map, "next_if_true", step.getNextIfTrue());
        putIfNotNull(map, "next_if_false", step.getNextIfFalse());
        if (step.getPreProcess() != null && !step.getPreProcess().isEmpty()) {
            map.put("pre_process", marshalProcessor(step.getPreProcess()));
        }
        if (step.getPostProcess() != null && !step.getPostProcess().isEmpty()) {
            map.put("post_process", marshalProcessor(step.getPostProcess()));
        }
        return map;
    }

    private Map<String, Object> marshalProcessor(Map<String, Object> processor) {
        Map<String, Object> map = new LinkedHashMap<>();
        expandToNameValueList(processor, INJECT, map);
        expandToNameValueList(processor, EXTRACT, map);

        Object delay = processor.get(DELAY);
        if (delay != null) {
            map.put(DELAY, delay);
        }

        return map;
    }

    private void expandToNameValueList(Map<String, Object> source, String key, Map<String, Object> target) {
        Object obj = source.get(key);
        if (!(obj instanceof Map<?, ?> entries) || entries.isEmpty()) return;

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            list.add(Map.of("name", entry.getKey(), VALUE, entry.getValue()));
        }
        target.put(key, list);
    }

    protected void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    protected void putIfNotEmpty(Map<String, Object> map, String key, Map<String, Object> value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    protected Map<String, Object> getMap(Map<String, Object> parent, String key) {
        if (parent == null) return Collections.emptyMap();
        Object value = parent.get(key);
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    protected List<Map<String, Object>> getList(Map<String, Object> parent, String key) {
        if (parent == null) return Collections.emptyList();
        Object value = parent.get(key);
        if (value instanceof List<?>) {
            return (List<Map<String, Object>>) value;
        }
        return Collections.emptyList();
    }

    protected String getString(Map<String, Object> map, String key) {
        return getString(map, key, null);
    }

    protected String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    protected int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException _) { return defaultValue; }
        }
        return defaultValue;
    }

    protected boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
