package dev.zeann3th.stresspilot.infrastructure.adapters.store.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import dev.zeann3th.stresspilot.core.ports.store.MetricScrapeEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MetricScrapeEventStoreAdapter implements MetricScrapeEventStore {
    private final JpaMetricScrapeEventRepository repository;

    @Override
    public MetricScrapeEventEntity save(MetricScrapeEventEntity event) {
        return repository.save(event);
    }

    @Override
    public void saveAll(List<MetricScrapeEventEntity> events) {
        repository.saveAll(events);
    }

    @Override
    public List<MetricScrapeEventEntity> findByRunId(String runId) {
        return repository.findByRunId(runId);
    }
}
