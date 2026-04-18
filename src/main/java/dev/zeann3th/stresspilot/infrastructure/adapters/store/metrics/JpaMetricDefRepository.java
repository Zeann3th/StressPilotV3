package dev.zeann3th.stresspilot.infrastructure.adapters.store.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricDefEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JpaMetricDefRepository extends JpaRepository<MetricDefEntity, Long> {
    Optional<MetricDefEntity> findByName(String name);
}
