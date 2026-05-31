package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "[DistributedEventPublisher]")
@Component
public class DistributedEventPublisher {
    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final JsonMapper jsonMapper;

    @Autowired
    public DistributedEventPublisher(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${application.distributed.key-prefix:stresspilot}") String keyPrefix
    ) {
        this(redisTemplateProvider.getIfAvailable(), keyPrefix, new JsonMapper());
    }

    DistributedEventPublisher(StringRedisTemplate redisTemplate, String keyPrefix, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.jsonMapper = jsonMapper;
    }

    public boolean isAvailable() {
        return redisTemplate != null;
    }

    public void publishRequestLog(RequestLogEntity logEntity) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Distributed request log publishing requires Redis to be enabled");
        }

        RequestLogPayload payload = RequestLogPayload.from(logEntity);
        try {
            redisTemplate.convertAndSend(
                    new DistributedChannels(keyPrefix).requestLogChannel(),
                    jsonMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Failed to publish distributed request log for run {}: {}", payload.runId(), e.getMessage());
        }
    }

    public void publishWorkload(WorkloadPayload payload) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Distributed workload publishing requires Redis to be enabled");
        }

        try {
            redisTemplate.convertAndSend(
                    new DistributedChannels(keyPrefix).workChannel(),
                    jsonMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to publish distributed workload for run %s to node %s"
                            .formatted(payload.runId(), payload.targetNodeId()),
                    e);
        }
    }

    public void publishStop(String runId) {
        if (redisTemplate == null) {
            throw new IllegalStateException("Distributed stop publishing requires Redis to be enabled");
        }

        try {
            redisTemplate.convertAndSend(
                    new DistributedChannels(keyPrefix).stopChannel(),
                    runId);
        } catch (Exception e) {
            log.warn("Failed to publish distributed stop for run {}: {}", runId, e.getMessage());
        }
    }

    public record RequestLogPayload(
            String runId,
            Long endpointId,
            Integer statusCode,
            Boolean success,
            Long responseTime,
            String request,
            String response,
            LocalDateTime createdAt) {
        static RequestLogPayload from(RequestLogEntity logEntity) {
            return new RequestLogPayload(
                    logEntity.getRunId(),
                    logEntity.getEndpointId(),
                    logEntity.getStatusCode(),
                    logEntity.getSuccess(),
                    logEntity.getResponseTime(),
                    logEntity.getRequest(),
                    logEntity.getResponse(),
                    logEntity.getCreatedAt());
        }
    }

    public record WorkloadPayload(
            String marker,
            String runId,
            Long flowId,
            String targetNodeId,
            int assignedThreads,
            int totalDuration,
            int rampUpDuration,
            long deadline,
            String flowType,
            LocalDateTime runStartedAt,
            Map<String, Object> variables,
            List<Map<String, Object>> credentials,
            Map<String, Object> baseEnvironment,
            List<FlowStepPayload> steps) {
        public static WorkloadPayload from(FlowExecutionContext context, String targetNodeId, int assignedThreads) {
            return new WorkloadPayload(
                    "DISTRIBUTED_WORKLOAD",
                    context.getRunId(),
                    context.getRun().getFlowId(),
                    targetNodeId,
                    assignedThreads,
                    context.getCommand().getTotalDuration(),
                    context.getCommand().getRampUpDuration(),
                    context.getDeadline(),
                    context.getFlowType(),
                    context.getRun().getStartedAt(),
                    context.getCommand().getVariables(),
                    context.getCommand().getCredentials(),
                    context.getBaseEnvironment(),
                    context.getSteps().stream().map(FlowStepPayload::from).toList());
        }
    }

    public record FlowStepPayload(
            String id,
            String type,
            Long endpointId,
            EndpointPayload endpoint,
            String preProcessor,
            String postProcessor,
            String nextIfTrue,
            String nextIfFalse,
            String condition) {
        static FlowStepPayload from(dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity step) {
            return new FlowStepPayload(
                    step.getId(),
                    step.getType(),
                    step.getEndpointId(),
                    step.getEndpoint() != null ? EndpointPayload.from(step.getEndpoint()) : null,
                    step.getPreProcessor(),
                    step.getPostProcessor(),
                    step.getNextIfTrue(),
                    step.getNextIfFalse(),
                    step.getCondition());
        }
    }

    public record EndpointPayload(
            Long id,
            String name,
            String description,
            String type,
            String url,
            String body,
            String successCondition,
            String httpMethod,
            String httpHeaders,
            String httpParameters,
            String grpcServiceName,
            String grpcMethodName,
            String grpcStubPath) {
        static EndpointPayload from(dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity endpoint) {
            return new EndpointPayload(
                    endpoint.getId(),
                    endpoint.getName(),
                    endpoint.getDescription(),
                    endpoint.getType(),
                    endpoint.getUrl(),
                    endpoint.getBody(),
                    endpoint.getSuccessCondition(),
                    endpoint.getHttpMethod(),
                    endpoint.getHttpHeaders(),
                    endpoint.getHttpParameters(),
                    endpoint.getGrpcServiceName(),
                    endpoint.getGrpcMethodName(),
                    endpoint.getGrpcStubPath());
        }
    }
}
