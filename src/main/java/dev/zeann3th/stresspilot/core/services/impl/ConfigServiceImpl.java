package dev.zeann3th.stresspilot.core.services.impl;

import dev.zeann3th.stresspilot.core.domain.entities.ConfigEntity;
import dev.zeann3th.stresspilot.core.ports.store.ConfigStore;
import dev.zeann3th.stresspilot.core.services.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {
    private final ConfigStore configStore;

    @Override
    public Map<String, String> getAllConfigs() {
        return configStore.findAll().stream()
                .collect(Collectors.toMap(
                        ConfigEntity::getKey,
                        ConfigEntity::getValue
                ));
    }

    @Override
    public Optional<String> getValue(String key) {
        return configStore.findByKey(key)
                .map(ConfigEntity::getValue)
                .filter(v -> !v.isBlank());
    }

    @Override
    public void setValue(String key, String value) {
        ConfigEntity configEntity = configStore.findByKey(key)
                .orElse(ConfigEntity.builder().key(key).build());
        configEntity.setValue(value);
        configStore.save(configEntity);
    }
}
