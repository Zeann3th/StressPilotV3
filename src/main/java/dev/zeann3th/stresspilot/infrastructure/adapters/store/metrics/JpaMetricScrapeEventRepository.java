package dev.zeann3th.stresspilot.infrastructure.adapters.store.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JpaMetricScrapeEventRepository extends JpaRepository<MetricScrapeEventEntity, Long> {
    List<MetricScrapeEventEntity> findByRunId(String runId);
}
