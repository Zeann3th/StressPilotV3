package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DistributedMasterLogSubscriberTest {
    @Test
    void handleRequestLogMessageReconstructsEntityWithLightweightReferences() {
        RequestLogService requestLogService = mock(RequestLogService.class);
        DistributedMasterLogSubscriber subscriber = new DistributedMasterLogSubscriber(
                requestLogService,
                "stresspilot",
                new JsonMapper());
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 24, 21, 0);
        DistributedEventPublisher.RequestLogPayload payload = new DistributedEventPublisher.RequestLogPayload(
                "run-1",
                11L,
                500,
                false,
                99L,
                "request-debug",
                "response-body",
                createdAt);

        subscriber.handleRequestLogMessage(new JsonMapper().writeValueAsString(payload));

        ArgumentCaptor<RequestLogEntity> captor = ArgumentCaptor.forClass(RequestLogEntity.class);
        verify(requestLogService).queueLog(captor.capture());
        RequestLogEntity log = captor.getValue();
        assertThat(log.getRun().getId()).isEqualTo("run-1");
        assertThat(log.getEndpoint().getId()).isEqualTo(11L);
        assertThat(log.getStatusCode()).isEqualTo(500);
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getResponseTime()).isEqualTo(99L);
        assertThat(log.getRequest()).isEqualTo("request-debug");
        assertThat(log.getResponse()).isEqualTo("response-body");
        assertThat(log.getCreatedAt()).isEqualTo(createdAt);
    }
}
