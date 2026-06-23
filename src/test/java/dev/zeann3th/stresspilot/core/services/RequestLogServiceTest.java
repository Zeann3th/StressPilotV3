package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.config.RequestLogSamplingProperties;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLogServiceTest {

    @Test
    void samplingDisabledWritesEveryLog() {
        CountingWriter writer = new CountingWriter();
        RequestLogSamplingProperties sampling = new RequestLogSamplingProperties();
        sampling.setEnabled(false);
        RequestLogService service = new RequestLogService(List.of(writer), sampling);

        for (int i = 0; i < 100; i++) {
            service.queueLog(RequestLogEntity.builder().build());
        }

        assertThat(writer.count.get()).isEqualTo(100);
    }

    @Test
    void zeroSamplingRateSkipsEveryLogWithoutCallingWriters() {
        CountingWriter writer = new CountingWriter();
        RequestLogSamplingProperties sampling = new RequestLogSamplingProperties();
        sampling.setEnabled(true);
        sampling.setRate(0.0);
        RequestLogService service = new RequestLogService(List.of(writer), sampling);

        for (int i = 0; i < 100; i++) {
            service.queueLog(RequestLogEntity.builder().build());
        }

        assertThat(writer.count.get()).isZero();
    }

    private static final class CountingWriter implements RequestMessagePort {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void write(RequestLogEntity log) {
            count.incrementAndGet();
        }

        @Override
        public void flush() {
            // This test writer records queued logs synchronously, so there is no buffered state to flush.
        }
    }
}
