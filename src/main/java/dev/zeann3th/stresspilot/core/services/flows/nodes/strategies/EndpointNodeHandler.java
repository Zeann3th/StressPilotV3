package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j(topic = "[EndpointNodeHandler]")
@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class EndpointNodeHandler implements FlowNodeHandler {

    private boolean strictLinear;

    private final EndpointExecutorFactory executorFactory;
    private final RequestLogService requestLogService;
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

        context.recordRequest(result.isSuccess());

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

        String responseText = result.getRawResponse();
        if (responseText == null || responseText.isBlank()) {
            responseText = String.format("[empty body] status=%d success=%s message=%s",
                    result.getStatusCode(), result.isSuccess(), result.getMessage());
        }

        requestLogService.queueLog(RequestLogEntity.builder()
                .run(context.getRun())
                .endpoint(endpoint)
                .statusCode(result.getStatusCode())
                .success(result.isSuccess())
                .responseTime(result.getResponseTimeMs())
                .request(requestDebug.toString())
                .response(responseText)
                .createdAt(LocalDateTime.now())
                .build());

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
}
