package dev.zeann3th.stresspilot.core.services.configs;

import dev.zeann3th.stresspilot.core.domain.entities.ConfigEntity;
import dev.zeann3th.stresspilot.core.ports.store.ConfigStore;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {
    private final ConfigStore configStore;

    public Map<String, String> getAllConfigs() {
        return configStore.findAll().stream()
                .collect(Collectors.toMap(
                        ConfigEntity::getKey,
                        config -> config.getValue() == null ? "" : config.getValue()
                ));
    }

    public Map<String, String> getConfigsByKeys(Iterable<String> keys) {
        return configStore.findAllByKeyIn(keys).stream()
                .filter(config -> StringUtils.isNotBlank(config.getValue()))
                .collect(Collectors.toMap(
                        ConfigEntity::getKey,
                        ConfigEntity::getValue
                ));
    }

    public Optional<String> getValue(String key) {
        return configStore.findByKey(key)
                .map(ConfigEntity::getValue)
                .filter(StringUtils::isNotBlank);
    }

    public void setValue(String key, String value) {
        ConfigEntity configEntity = configStore.findByKey(key)
                .orElseGet(() -> ConfigEntity.builder().key(key).build());

        configEntity.setValue(value);
        configStore.save(configEntity);
    }
}
