package dev.zeann3th.stresspilot.core.services.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import dev.zeann3th.stresspilot.core.ports.message.MetricMessagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j(topic = "[MetricLogService]")
@Service
@RequiredArgsConstructor
public class MetricLogService {

    private final List<MetricMessagePort> writers;

    public void queueEvent(MetricScrapeEventEntity event) {
        writers.forEach(w -> {
            try {
                w.write(event);
            } catch (Exception e) {
                log.warn("Writer {} failed on write: {}", w.getClass().getSimpleName(), e.getMessage());
            }
        });
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
