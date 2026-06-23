package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.config.RequestLogSamplingProperties;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j(topic = "[RequestLogService]")
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final List<RequestMessagePort> writers;
    private final RequestLogSamplingProperties samplingProperties;

    public void queueLog(RequestLogEntity logEntity) {
        if (!shouldWriteSample()) {
            return;
        }
        writers.forEach(w -> {
            try {
                w.write(logEntity);
            } catch (Exception e) {
                log.warn("Writer {} failed on write: {}", w.getClass().getSimpleName(), e.getMessage());
            }
        });
    }

    private boolean shouldWriteSample() {
        if (!samplingProperties.isEnabled()) {
            return true;
        }
        double rate = samplingProperties.getRate();
        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    public void ensureFlushed() {
        writers.forEach(w -> {
            try {
                w.flush();
            } catch (Exception e) {
                log.warn("Writer {} failed on flush: {}", w.getClass().getSimpleName(), e.getMessage());
            }
        });
    }
}
