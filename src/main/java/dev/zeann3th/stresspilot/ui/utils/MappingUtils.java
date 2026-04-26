package dev.zeann3th.stresspilot.ui.utils;

import lombok.experimental.UtilityClass;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class MappingUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Map<String, Object> mapToObjectMap(String value) {
        if (value == null || value.isBlank())
            return new HashMap<>();
        try {
            return MAPPER.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("error", value);
        }
    }

    public static Map<String, String> mapToStringMap(String value) {
        if (value == null || value.isBlank())
            return new HashMap<>();
        try {
            return MAPPER.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("error", value);
        }
    }
}
