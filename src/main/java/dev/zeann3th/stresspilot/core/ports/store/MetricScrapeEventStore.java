package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import java.util.List;

public interface MetricScrapeEventStore {
    MetricScrapeEventEntity save(MetricScrapeEventEntity event);

    void saveAll(List<MetricScrapeEventEntity> events);

    List<MetricScrapeEventEntity> findByRunId(String runId);
}
