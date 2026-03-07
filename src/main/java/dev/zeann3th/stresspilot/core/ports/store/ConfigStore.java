package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.ConfigEntity;

import java.util.List;
import java.util.Optional;

public interface ConfigStore {
    ConfigEntity save(ConfigEntity configEntity);

    List<ConfigEntity> findAllByKeyIn(Iterable<String> keys);

    Optional<ConfigEntity> findByKey(String key);

    List<ConfigEntity> findAll();
}
