package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.MetricDefEntity;
import java.util.Optional;

public interface MetricDefStore {
    MetricDefEntity save(MetricDefEntity metricDef);
    Optional<MetricDefEntity> findByName(String name);
}
