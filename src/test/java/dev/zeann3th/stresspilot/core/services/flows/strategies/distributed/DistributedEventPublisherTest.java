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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedEventPublisherTest {
    @Test
    void isAvailableReturnsFalseWhenRedisTemplateIsMissing() {
        DistributedEventPublisher publisher = new DistributedEventPublisher(null, "stresspilot", new JsonMapper());

        org.assertj.core.api.Assertions.assertThat(publisher.isAvailable()).isFalse();
    }

    @Test
    void isAvailableReturnsTrueWhenRedisTemplateExists() {
        DistributedEventPublisher publisher = new DistributedEventPublisher(
                mock(StringRedisTemplate.class),
                "stresspilot",
                new JsonMapper());

        org.assertj.core.api.Assertions.assertThat(publisher.isAvailable()).isTrue();
    }

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
                .correlationId("123456789012345678")
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
                "123456789012345678",
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

    @Test
    void publishStopSendsRunIdToStopChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DistributedEventPublisher publisher = new DistributedEventPublisher(redisTemplate, "stresspilot", new JsonMapper());

        publisher.publishStop("run-1");

        verify(redisTemplate).convertAndSend("stresspilot:distributed:stop", "run-1");
    }

    @Test
    void publishWorkloadPropagatesPublishFailures() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        DistributedEventPublisher publisher = new DistributedEventPublisher(redisTemplate, "stresspilot", new JsonMapper());
        DistributedEventPublisher.WorkloadPayload payload = workloadPayload();
        when(redisTemplate.convertAndSend("stresspilot:distributed:work", new JsonMapper().writeValueAsString(payload)))
                .thenThrow(new IllegalStateException("Redis down"));

        assertThatThrownBy(() -> publisher.publishWorkload(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish distributed workload for run run-1 to node worker-a");
    }

    private static DistributedEventPublisher.WorkloadPayload workloadPayload() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 24, 21, 0);
        return new DistributedEventPublisher.WorkloadPayload(
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
    }
}
