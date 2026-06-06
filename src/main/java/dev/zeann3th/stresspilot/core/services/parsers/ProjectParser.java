package dev.zeann3th.stresspilot.core.services.parsers;

import dev.zeann3th.stresspilot.core.domain.commands.project.ImportProjectCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandException;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.flows.FlowProcessor;
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
    private static final String SET = "set";
    private static final String INCREMENT = "increment";
    private static final String APPEND = "append";
    private static final String SERIALIZE_JSON = "serialize_json";
    private static final String CLEAR = "clear";
    private static final String DELAY = "delay";
    private static final String DESCRIPTION = "description";
    private static final String VALUE = "value";
    private static final String DEFAULT_ENVIRONMENT_NAME = "Environment";

    public ImportProjectCommand unmarshal(String spec) {
        Map<String, Object> root = parseRoot(spec);

        Map<String, Object> project = getMap(root, "project");
        List<Map<String, Object>> envNodes = getList(root, "environment");
        List<Map<String, Object>> environmentNodes = getList(root, "environments");
        List<Map<String, Object>> endpointNodes = getList(root, "endpoints");
        List<Map<String, Object>> flowNodes = getList(root, "flows");

        List<ImportProjectCommand.EnvVar> envVars = new ArrayList<>();
        for (Map<String, Object> env : envNodes) {
            envVars.add(buildEnvVar(env));
        }

        List<ImportProjectCommand.Environment> environments = new ArrayList<>();
        for (Map<String, Object> env : environmentNodes) {
            List<ImportProjectCommand.EnvVar> vars = new ArrayList<>();
            for (Map<String, Object> variable : getList(env, "variables")) {
                vars.add(buildEnvVar(variable));
            }
            environments.add(new ImportProjectCommand.Environment(
                    getString(env, "name", DEFAULT_ENVIRONMENT_NAME),
                    getBoolean(env, "active", false),
                    vars
            ));
        }
        if (environments.isEmpty() && !envVars.isEmpty()) {
            environments.add(new ImportProjectCommand.Environment("Default", true, envVars));
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
                .environments(environments)
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

        if (command.getEnvironments() != null && !command.getEnvironments().isEmpty()) {
            List<Map<String, Object>> envList = new ArrayList<>();
            for (ImportProjectCommand.Environment env : command.getEnvironments()) {
                Map<String, Object> envMap = new LinkedHashMap<>();
                envMap.put("name", env.getName());
                if (env.getActive() != null && env.getActive()) {
                    envMap.put("active", true);
                }
                List<Map<String, Object>> variables = new ArrayList<>();
                if (env.getVariables() != null) {
                    for (ImportProjectCommand.EnvVar v : env.getVariables()) {
                        Map<String, Object> varMap = new LinkedHashMap<>();
                        varMap.put("name", v.getName());
                        varMap.put(VALUE, v.getValue());
                        if (v.getActive() != null && !v.getActive()) {
                            varMap.put("active", false);
                        }
                        variables.add(varMap);
                    }
                }
                if (!variables.isEmpty()) {
                    envMap.put("variables", variables);
                }
                envList.add(envMap);
            }
            stresspilot.put("environments", envList);
        } else if (command.getEnvironment() != null && !command.getEnvironment().isEmpty()) {
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
        return new ImportProjectCommand.Flow(
                getString(flowNode, "name"),
                getString(flowNode, DESCRIPTION),
                getString(flowNode, "type", "DEFAULT").toUpperCase(),
                steps);
    }

    private ImportProjectCommand.EnvVar buildEnvVar(Map<String, Object> env) {
        return new ImportProjectCommand.EnvVar(
                getString(env, "name"),
                getString(env, VALUE),
                getBoolean(env, "active", true)
        );
    }

    private ImportProjectCommand.Step buildStep(Map<String, Object> stepNode) {
        Map<String, Object> preProcess = new LinkedHashMap<>(buildProcessor(getMap(stepNode, "pre_process")));
        Map<String, Object> postProcess = new LinkedHashMap<>(buildProcessor(getMap(stepNode, "post_process")));
        putIfNotNull(preProcess, FlowProcessor.RUN_IF, stepNode.get(FlowProcessor.RUN_IF));
        putIfNotNull(preProcess, FlowProcessor.SKIP_IF, stepNode.get(FlowProcessor.SKIP_IF));

        Map<String, Object> loop = buildLoopConfig(stepNode);
        if (!loop.isEmpty()) {
            preProcess.put("loop", loop);
        }

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
        collectProcessorMap(processNode, SET, processor);
        collectProcessorMap(processNode, INCREMENT, processor);
        collectProcessorMap(processNode, APPEND, processor);
        collectProcessorMap(processNode, SERIALIZE_JSON, processor);
        putIfNotNull(processor, CLEAR, processNode.get(CLEAR));
        putIfNotNull(processor, FlowProcessor.RUN_IF, processNode.get(FlowProcessor.RUN_IF));
        putIfNotNull(processor, FlowProcessor.SKIP_IF, processNode.get(FlowProcessor.SKIP_IF));
        putIfNotNull(processor, "loop", processNode.get("loop"));

        Object delay = processNode.get(DELAY);
        if (delay != null) {
            processor.put(DELAY, delay);
        }

        return processor;
    }

    private Map<String, Object> buildLoopConfig(Map<String, Object> stepNode) {
        Map<String, Object> loop = new LinkedHashMap<>();
        for (String key : List.of("source", "item", "index", "body", "count")) {
            putIfNotNull(loop, key, stepNode.get(key));
        }
        return loop;
    }

    @SuppressWarnings("unchecked")
    private void collectProcessorMap(Map<String, Object> source, String key, Map<String, Object> target) {
        Object obj = source.get(key);
        if (obj instanceof Map<?, ?> map && !map.isEmpty()) {
            target.put(key, new LinkedHashMap<>((Map<String, Object>) map));
            return;
        }

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
        putIfNotNull(map, "type", flow.getType());

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
        putIfNotEmpty(map, SET, processor.get(SET));
        putIfNotEmpty(map, INCREMENT, processor.get(INCREMENT));
        putIfNotEmpty(map, APPEND, processor.get(APPEND));
        putIfNotEmpty(map, SERIALIZE_JSON, processor.get(SERIALIZE_JSON));
        putIfNotNull(map, CLEAR, processor.get(CLEAR));
        putIfNotNull(map, FlowProcessor.RUN_IF, processor.get(FlowProcessor.RUN_IF));
        putIfNotNull(map, FlowProcessor.SKIP_IF, processor.get(FlowProcessor.SKIP_IF));
        putIfNotEmpty(map, "loop", processor.get("loop"));

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

    protected void putIfNotEmpty(Map<String, Object> map, String key, Object value) {
        if (value instanceof Map<?, ?> valueMap && !valueMap.isEmpty()) {
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
