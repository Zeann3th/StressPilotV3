package dev.zeann3th.stresspilot.core.services;

import java.util.Map;
import java.util.Optional;

public interface ConfigService {
    Map<String, String> getAllConfigs();

    Optional<String> getValue(String key);

    void setValue(String key, String value);
}
