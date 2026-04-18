package dev.zeann3th.stresspilot.infrastructure.adapters.store.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricDefEntity;
import dev.zeann3th.stresspilot.core.ports.store.MetricDefStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MetricDefStoreAdapter implements MetricDefStore {
    private final JpaMetricDefRepository repository;

    @Override
    public MetricDefEntity save(MetricDefEntity metricDef) {
        return repository.save(metricDef);
    }

    @Override
    public Optional<MetricDefEntity> findByName(String name) {
        return repository.findByName(name);
    }
}
