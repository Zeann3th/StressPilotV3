package dev.zeann3th.stresspilot.core.services.configs;

import java.util.Map;
import java.util.Optional;

public interface ConfigService {
    Map<String, String> getAllConfigs();

    Map<String, String> getConfigsByKeys(Iterable<String> keys);

    Optional<String> getValue(String key);

    void setValue(String key, String value);
}

