package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.ConfigService;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorServiceFactory;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorUtils;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j(topic = "[EndpointNodeHandler]")
@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class EndpointNodeHandler implements FlowNodeHandler {

    private boolean strictLinear;
    private static final Random RANDOM = new Random();

    private final EndpointExecutorServiceFactory executorFactory;
    private final RequestLogService requestLogService;
    private final JsonMapper jsonMapper;
    private final ConfigService configService;

    @PostConstruct
    public void init() {
        this.strictLinear = Boolean.parseBoolean(
                configService.getValue(ConfigKey.FLOW_ENDPOINT_STRICT_LINEAR.name()
                ).orElse("false")
        );
        log.info("Endpoint strict linear routing initialized as: {}", this.strictLinear);
    }

    @Override
    public FlowStepType getSupportedType() {
        return FlowStepType.ENDPOINT;
    }

    @Override
    public String handle(FlowStepEntity step,
            Map<String, FlowStepEntity> stepMap,
            FlowExecutionContext context) {

        if (step.getPreProcessor() != null && !step.getPreProcessor().isBlank()) {
            process(step.getPreProcessor(), context.getVariables(),
                    null, "pre-processor", context.getThreadId());
        }

        EndpointEntity endpoint = step.getEndpoint();
        if (endpoint == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0005);
        }

        long start = System.currentTimeMillis();
        ExecuteEndpointResponse result;
        try {
            result = executorFactory.getExecutor(endpoint.getType())
                    .execute(endpoint, context.getVariables(), context.getExecutionContext());
            EndpointExecutorUtils.evaluateSuccessCondition(endpoint, result);
        } catch (Exception e) {
            Map<String, Object> errData = new HashMap<>();
            errData.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            result = ExecuteEndpointResponse.builder()
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .data(errData)
                    .rawResponse(errData.toString())
                    .build();
        }

        Map<String, Object> endpointDebug = new LinkedHashMap<>();
        endpointDebug.put("id", endpoint.getId());
        endpointDebug.put("name", endpoint.getName());
        endpointDebug.put("type", endpoint.getType());
        endpointDebug.put("url", endpoint.getUrl());
        if ("HTTP".equalsIgnoreCase(endpoint.getType())) {
            endpointDebug.put("method", endpoint.getHttpMethod());
            endpointDebug.put("headers", endpoint.getHttpHeaders());
            endpointDebug.put("parameters", endpoint.getHttpParameters());
        } else if ("GRPC".equalsIgnoreCase(endpoint.getType())) {
            endpointDebug.put("service", endpoint.getGrpcServiceName());
            endpointDebug.put("method", endpoint.getGrpcMethodName());
        }
        endpointDebug.put("body", endpoint.getBody());

        Map<String, Object> requestDebug = new LinkedHashMap<>();
        requestDebug.put("endpoint", endpointDebug);
        requestDebug.put("variables_snapshot", new HashMap<>(context.getVariables()));

        requestLogService.queueLog(RequestLogEntity.builder()
                .run(context.getRun())
                .endpoint(endpoint)
                .statusCode(result.getStatusCode())
                .success(result.isSuccess())
                .responseTime(result.getResponseTimeMs())
                .request(requestDebug.toString())
                .response(result.getRawResponse())
                .createdAt(LocalDateTime.now())
                .build());

        if (step.getPostProcessor() != null && !step.getPostProcessor().isBlank()) {
            process(step.getPostProcessor(), context.getVariables(),
                    result.getData(), "post-processor", context.getThreadId());
        }

        if (strictLinear) {
            return step.getNextIfTrue();
        } else {
            if (result.isSuccess() && step.getNextIfTrue() != null) {
                return step.getNextIfTrue();
            }
            return step.getNextIfFalse();
        }
    }

    private void process(String processorJson,
            Map<String, Object> variables,
            Object prevResponse,
            String processorType,
            int threadId) {
        if (processorJson == null || processorJson.isBlank())
            return;
        try {
            Map<String, Object> proc = jsonMapper.readValue(processorJson, new TypeReference<>() {
            });
            if (CollectionUtils.isEmpty(proc))
                return;

            if (proc.containsKey("sleep")) {
                long base = Long.parseLong(proc.get("sleep").toString());
                long jitter = 500L + RANDOM.nextInt(501);
                Thread.sleep(base + jitter);
            }

            if (proc.get("clear") instanceof List<?> keysToClear) {
                keysToClear.forEach(key -> variables.remove(String.valueOf(key)));
            }

            if (proc.containsKey("inject")) {
                Object injectObj = proc.get("inject");
                if (injectObj != null) {
                    Map<String, Object> inject = jsonMapper.convertValue(injectObj, new TypeReference<>() {
                    });
                    variables.putAll(inject);
                }
            }

            if (proc.containsKey("extract") && prevResponse != null) {
                Object extractObj = proc.get("extract");
                if (extractObj != null) {
                    Map<String, Object> extract = jsonMapper.convertValue(extractObj, new TypeReference<>() {
                    });
                    for (Map.Entry<String, Object> entry : extract.entrySet()) {
                        String key = entry.getKey();
                        String path = String.valueOf(entry.getValue());
                        Object value;
                        if (".".equals(path) || "@".equals(path)) {
                            value = prevResponse;
                        } else if (path.startsWith("$")) {
                            value = resolvePathDollar(prevResponse, path.substring(1));
                        } else {
                            value = resolvePath(prevResponse, path);
                        }
                        if (value != null)
                            variables.put(key, value);
                    }
                }
            }

        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing {} for thread {}: {}", processorType, threadId, e.getMessage());
        }
    }

    private static Object resolvePath(Object obj, String path) {
        if (obj == null || path == null || path.isBlank())
            return null;
        String[] parts = path.replaceAll("\\[(\\w+)]", ".$1").split("\\.");
        Object current = obj;
        for (String part : parts) {
            if (current == null)
                return null;
            switch (current) {
                case Map<?, ?> map -> current = map.get(part);
                case List<?> list -> {
                    try {
                        int idx = Integer.parseInt(part);
                        current = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
                    } catch (NumberFormatException _) {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
        }
        return current;
    }

    private Object resolvePathDollar(Object obj, String spec) {
        if (obj == null || spec == null || spec.isBlank())
            return null;
        List<Map.Entry<String, Object>> flat = new ArrayList<>();
        DataUtils.flattenObject(obj, "", flat);

        if (spec.contains(".")) {
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().equals(spec))
                    return e.getValue();
            }
            String suffix = "." + spec;
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().endsWith(suffix))
                    return e.getValue();
            }
        } else {
            for (Map.Entry<String, Object> e : flat) {
                String p = e.getKey();
                String last = p.contains(".") ? p.substring(p.lastIndexOf('.') + 1) : p;
                if (last.equals(spec))
                    return e.getValue();
            }
        }
        return null;
    }
}
