package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j(topic = "[DistributedMasterLogSubscriber]")
@Component
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedMasterLogSubscriber implements MessageListener {
    private final RequestLogService requestLogService;
    private final RunStore runStore;
    private final String keyPrefix;
    private final JsonMapper jsonMapper;
    private final StringRedisTemplate redisTemplate;
    private RedisMessageListenerContainer listenerContainer;

    @Autowired
    public DistributedMasterLogSubscriber(
            RequestLogService requestLogService,
            RunStore runStore,
            StringRedisTemplate redisTemplate,
            @Value("${application.distributed.key-prefix:stresspilot}") String keyPrefix
    ) {
        this(requestLogService, runStore, redisTemplate, keyPrefix, new JsonMapper());
    }

    DistributedMasterLogSubscriber(
            RequestLogService requestLogService,
            RunStore runStore,
            String keyPrefix,
            JsonMapper jsonMapper
    ) {
        this(requestLogService, runStore, null, keyPrefix, jsonMapper);
    }

    private DistributedMasterLogSubscriber(
            RequestLogService requestLogService,
            RunStore runStore,
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            JsonMapper jsonMapper
    ) {
        this.requestLogService = requestLogService;
        this.runStore = runStore;
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    void subscribe() {
        if (redisTemplate == null) {
            return;
        }

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisTemplate.getConnectionFactory());
        listenerContainer.addMessageListener(this, new ChannelTopic(new DistributedChannels(keyPrefix).requestLogChannel()));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @PreDestroy
    void unsubscribe() throws Exception {
        if (listenerContainer != null) {
            listenerContainer.destroy();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        handleRequestLogMessage(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    void handleRequestLogMessage(String message) {
        try {
            DistributedEventPublisher.RequestLogPayload payload = jsonMapper.readValue(
                    message,
                    DistributedEventPublisher.RequestLogPayload.class);
            if (payload.runId() == null) {
                return;
            }
            runStore.findById(payload.runId())
                    .map(run -> toEntity(payload, run))
                    .ifPresent(requestLogService::queueLog);
        } catch (Exception e) {
            log.warn("Failed to handle distributed request log: {}", e.getMessage());
        }
    }

    private RequestLogEntity toEntity(DistributedEventPublisher.RequestLogPayload payload, RunEntity run) {
        EndpointEntity endpoint = payload.endpointId() != null
                ? EndpointEntity.builder().id(payload.endpointId()).build()
                : null;

        return RequestLogEntity.builder()
                .run(run)
                .endpoint(endpoint)
                .statusCode(payload.statusCode())
                .success(payload.success())
                .responseTime(payload.responseTime())
                .request(payload.request())
                .response(payload.response())
                .createdAt(payload.createdAt())
                .build();
    }
}
