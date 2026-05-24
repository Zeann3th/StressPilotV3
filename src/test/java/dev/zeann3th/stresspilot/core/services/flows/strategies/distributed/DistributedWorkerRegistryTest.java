package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedWorkerRegistryTest {
    @Test
    void heartbeatStoresWorkerKeyWithTtlAndPublishesHeartbeat() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock();
        DistributedWorkerRegistry registry = new DistributedWorkerRegistry(redisTemplate, "stresspilot", "self", 15);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        registry.heartbeat("node-1");

        verify(valueOperations).set("stresspilot:workers:node-1", "node-1", Duration.ofSeconds(15));
        verify(redisTemplate).convertAndSend("stresspilot:distributed:worker:heartbeat", "node-1");
    }

    @Test
    void activeNodeIdsScansWorkerKeysAndReturnsStrippedNodeIds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        Cursor<String> cursor = mock();
        DistributedWorkerRegistry registry = new DistributedWorkerRegistry(redisTemplate, "stresspilot", "self", 15);

        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("stresspilot:workers:node-1", "stresspilot:workers:node-2");

        Set<String> nodeIds = registry.activeNodeIds();

        var scanOptions = forClass(ScanOptions.class);
        verify(redisTemplate).scan(scanOptions.capture());
        assertThat(scanOptions.getValue().getPattern()).isEqualTo("stresspilot:workers:*");
        assertThat(nodeIds).containsExactly("node-1", "node-2");
    }
}
