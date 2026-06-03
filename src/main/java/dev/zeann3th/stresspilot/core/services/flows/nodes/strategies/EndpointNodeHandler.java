package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorFactory;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorUtils;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.services.flows.nodes.NodeHandlerResult;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedEventPublisher;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j(topic = "[EndpointNodeHandler]")
@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class EndpointNodeHandler implements FlowNodeHandler {

    private static final Pattern PATH_VAR_PATTERN = Pattern.compile("(?<=/):(\\w+)");

    private boolean strictLinear;

    private final EndpointExecutorFactory executorFactory;
    private final RequestLogService requestLogService;
    private final ConfigService configService;
    private final DistributedEventPublisher distributedEventPublisher;

    @PostConstruct
    public void init() {
        String key = ConfigKey.FLOW_ENDPOINT_STRICT_LINEAR.name();
        Optional<String> configuredValue = configService.getValue(key);
        String rawValue = configuredValue.orElse("false");
        this.strictLinear = Boolean.parseBoolean(rawValue);
        log.info("Endpoint strict linear config loaded: key={}, rawValue={}, defaulted={}, enabled={}",
                key, rawValue, configuredValue.isEmpty(), this.strictLinear);
    }

    @Override
    public String getSupportedType() {
        return FlowStepType.ENDPOINT.name();
    }

    @Override
    public NodeHandlerResult handle(FlowStepEntity step,
            Map<String, FlowStepEntity> stepMap,
            FlowExecutionContext context) {

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

        boolean recordableEndpoint = !"JS".equalsIgnoreCase(endpoint.getType());
        if (recordableEndpoint) {
            context.recordRequest(result.isSuccess());
        }

        String requestText = formatRequest(endpoint, context.getVariables());
        String reportRequestText = formatReportRequest(endpoint, context);

        String responseText = result.getRawResponse();
        if (responseText == null || responseText.isBlank()) {
            responseText = String.format("[empty body] status=%d success=%s message=%s",
                    result.getStatusCode(), result.isSuccess(), result.getMessage());
        }

        if (recordableEndpoint) {
            RequestLogEntity log = RequestLogEntity.builder()
                    .run(context.getRun())
                    .endpoint(endpoint)
                    .statusCode(result.getStatusCode())
                    .success(result.isSuccess())
                    .responseTime(result.getResponseTimeMs())
                    .correlationId(context.getCorrelationId())
                    .request(reportRequestText)
                    .response(responseText)
                    .createdAt(LocalDateTime.now())
                    .build();
            if (context.isPersistRequestLogs()) {
                if (context.isDistributedWorker()) {
                    distributedEventPublisher.publishRequestLog(log);
                } else {
                    requestLogService.queueLog(log);
                }
            } else {
                context.recordDryRunRequestLog(toDryRunLog(log, endpoint, requestText));
            }
        }

        String nextId;
        if (strictLinear) {
            nextId = step.getNextIfTrue();
        } else {
            if (result.isSuccess() && step.getNextIfTrue() != null) {
                nextId = step.getNextIfTrue();
            } else {
                nextId = step.getNextIfFalse();
            }
        }
        
        return new NodeHandlerResult(nextId, result.getData());
    }

    private String formatRequest(EndpointEntity endpoint, Map<String, Object> variables) {
        return DataUtils.parseObjToJson(requestMap(endpoint, variables));
    }

    private String formatReportRequest(EndpointEntity endpoint, FlowExecutionContext context) {
        Map<String, Object> request = requestMap(endpoint, context.getVariables());
        request.put("variables_snapshot", reportVariablesSnapshot(context));
        return DataUtils.parseObjToJson(request);
    }

    private Map<String, Object> requestMap(EndpointEntity endpoint, Map<String, Object> variables) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("endpointId", endpoint.getId());
        request.put("endpointName", endpoint.getName());
        request.put("type", endpoint.getType());

        if ("HTTP".equalsIgnoreCase(endpoint.getType())) {
            request.put("method", endpoint.getHttpMethod());
            request.put("url", interpolateUrl(endpoint.getUrl(), variables));
            request.put("headers", interpolate(endpoint.getHttpHeaders(), variables));
            request.put("parameters", interpolate(endpoint.getHttpParameters(), variables));
            request.put("body", interpolate(endpoint.getBody(), variables));
        } else if ("GRPC".equalsIgnoreCase(endpoint.getType())) {
            request.put("target", interpolate(endpoint.getUrl(), variables));
            request.put("service", endpoint.getGrpcServiceName());
            request.put("method", endpoint.getGrpcMethodName());
            request.put("body", interpolate(endpoint.getBody(), variables));
        } else if ("JDBC".equalsIgnoreCase(endpoint.getType())) {
            request.put("url", interpolate(endpoint.getUrl(), variables));
            request.put("query", interpolate(endpoint.getBody(), variables));
        } else {
            request.put("url", interpolate(endpoint.getUrl(), variables));
            request.put("body", interpolate(endpoint.getBody(), variables));
        }

        return request;
    }

    private Map<String, Object> reportVariablesSnapshot(FlowExecutionContext context) {
        Map<String, Object> snapshot = new LinkedHashMap<>(context.getVariables());
        snapshot.put("__stresspilot_active_threads", context.getActiveThreadCount().get());
        return snapshot;
    }

    private String interpolateUrl(String raw, Map<String, Object> variables) {
        String value = interpolate(raw, variables);
        if (value == null || !value.contains(":")) {
            return value;
        }
        return PATH_VAR_PATTERN.matcher(value).replaceAll(match -> {
            String key = match.group(1);
            Object replacement = variables != null ? variables.get(key) : null;
            return replacement != null ? replacement.toString() : match.group(0);
        });
    }

    private String interpolate(String raw, Map<String, Object> variables) {
        if (raw == null) return null;
        String value = raw;
        if (value.contains("{{")) {
            value = DataUtils.replaceVariables(value, variables);
        }
        if (value.contains("@{")) {
            value = MockDataUtils.interpolate(value);
        }
        return value;
    }

    private RequestLog toDryRunLog(RequestLogEntity log, EndpointEntity endpoint, String requestText) {
        return RequestLog.builder()
                .endpointId(endpoint.getId())
                .endpointName(endpoint.getName())
                .statusCode(log.getStatusCode())
                .success(log.getSuccess())
                .responseTime(log.getResponseTime())
                .correlationId(log.getCorrelationId())
                .request(requestText)
                .response(log.getResponse())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
