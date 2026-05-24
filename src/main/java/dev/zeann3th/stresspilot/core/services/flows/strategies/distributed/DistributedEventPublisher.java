package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

@Slf4j(topic = "[DistributedEventPublisher]")
@Component
public class DistributedEventPublisher {
    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final JsonMapper jsonMapper;

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
}
