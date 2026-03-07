package dev.zeann3th.stresspilot.infrastructure.adapters.store.configs;

import dev.zeann3th.stresspilot.core.domain.entities.ConfigEntity;
import dev.zeann3th.stresspilot.core.ports.store.ConfigStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigStoreAdapter implements ConfigStore {
    private final ConfigJpaRepository configJpaRepository;

    @Override
    public ConfigEntity save(ConfigEntity configEntity) {
        return configJpaRepository.save(configEntity);
    }

    @Override
    public List<ConfigEntity> findAllByKeyIn(Iterable<String> keys) {
        return configJpaRepository.findAllByKeyIn(keys);
    }

    @Override
    public Optional<ConfigEntity> findByKey(String key) {
        return configJpaRepository.findByKey(key);
    }

    @Override
    public java.util.List<ConfigEntity> findAll() {
        return configJpaRepository.findAll();
    }
}
