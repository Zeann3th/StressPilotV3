package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DistributedEventPublisherTest {
    @Test
    void publishRequestLogSendsScalarPayloadToRequestLogChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DistributedEventPublisher publisher = new DistributedEventPublisher(redisTemplate, "stresspilot", new JsonMapper());
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 24, 20, 45);

        publisher.publishRequestLog(RequestLogEntity.builder()
                .run(RunEntity.builder().id("run-1").build())
                .endpoint(EndpointEntity.builder().id(11L).build())
                .statusCode(201)
                .success(true)
                .responseTime(42L)
                .request("request-debug")
                .response("response-body")
                .createdAt(createdAt)
                .build());

        DistributedEventPublisher.RequestLogPayload payload = new DistributedEventPublisher.RequestLogPayload(
                "run-1",
                11L,
                201,
                true,
                42L,
                "request-debug",
                "response-body",
                createdAt);
        verify(redisTemplate).convertAndSend(
                "stresspilot:distributed:request-log",
                new JsonMapper().writeValueAsString(payload));
    }

    @Test
    void publishWorkloadSendsScalarPayloadToWorkChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DistributedEventPublisher publisher = new DistributedEventPublisher(redisTemplate, "stresspilot", new JsonMapper());
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 24, 21, 0);
        DistributedEventPublisher.WorkloadPayload payload = new DistributedEventPublisher.WorkloadPayload(
                "DISTRIBUTED_WORKLOAD",
                "run-1",
                101L,
                "worker-a",
                3,
                60,
                5,
                123456789L,
                "DISTRIBUTED",
                startedAt,
                Map.of("region", "ap-southeast"),
                List.of(Map.of("token", "secret")),
                Map.of("host", "example.test"),
                List.of(new DistributedEventPublisher.FlowStepPayload(
                        "start",
                        "START",
                        null,
                        null,
                        null,
                        null,
                        "endpoint-1",
                        null,
                        null)));

        publisher.publishWorkload(payload);

        verify(redisTemplate).convertAndSend(
                "stresspilot:distributed:work",
                new JsonMapper().writeValueAsString(payload));
    }
}
