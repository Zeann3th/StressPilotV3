package dev.zeann3th.stresspilot.core.services.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricDefEntity;
import dev.zeann3th.stresspilot.core.ports.store.MetricDefStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "[MetricDefRegistry]")
@Component
@RequiredArgsConstructor
public class MetricDefRegistry {

    private final MetricDefStore store;
    private final ConcurrentHashMap<String, MetricDefEntity> cache
            = new ConcurrentHashMap<>();

    public MetricDefEntity getOrCreate(String name, String unit, String description) {
        return cache.computeIfAbsent(name, k -> store.findByName(k).orElseGet(() -> {
            log.debug("Registering new metric def: {}", k);
            return store.save(MetricDefEntity.builder()
                    .name(k)
                    .unit(unit)
                    .description(description)
                    .build());
        }));
    }
}
