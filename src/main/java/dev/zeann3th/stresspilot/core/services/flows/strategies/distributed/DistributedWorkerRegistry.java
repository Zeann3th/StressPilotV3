package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedWorkerRegistry {
    private final StringRedisTemplate redisTemplate;
    private final DistributedFlowProperties properties;

    public void heartbeat() {
        heartbeat(properties.getNodeId());
    }

    public void heartbeat(String nodeId) {
        DistributedChannels channels = channels();
        Duration ttl = Duration.ofSeconds(properties.getWorkerTtlSeconds());

        redisTemplate.opsForValue().set(channels.workerKey(nodeId), nodeId, ttl);
        redisTemplate.convertAndSend(channels.workerHeartbeatChannel(), nodeId);
    }

    public Set<String> activeNodeIds() {
        DistributedChannels channels = channels();
        String workerKeyPrefix = channels.workerKey("");
        ScanOptions options = ScanOptions.scanOptions()
                .match(channels.workerKey("*"))
                .count(100)
                .build();
        Set<String> nodeIds = new LinkedHashSet<>();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                nodeIds.add(cursor.next().substring(workerKeyPrefix.length()));
            }
        }

        return nodeIds;
    }

    private DistributedChannels channels() {
        return new DistributedChannels(properties.getKeyPrefix());
    }
}
