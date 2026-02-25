package dev.zeann3th.stresspilot.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j(topic = "DataUtils")
@UtilityClass
public class DataUtils {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String replaceVariables(String input, Map<String, Object> environment) {
        if (input == null || input.isEmpty() || environment == null || environment.isEmpty()) {
            return input;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = environment.get(key);
            String replacement = (value != null) ? String.valueOf(value) : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> replaceVariablesInMap(Map<String, Object> input, Map<String, Object> environment) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            switch (value) {
                case String valueStr -> result.put(entry.getKey(), replaceVariables(valueStr, environment));
                case Map<?,?> map -> result.put(entry.getKey(), replaceVariablesInMap((Map<String, Object>) value, environment));
                case List<?> list -> result.put(entry.getKey(), replaceVariablesInList(list, environment));
                case null, default -> result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> replaceVariablesInList(List<?> input, Map<String, Object> environment) {
        List<Object> result = new java.util.ArrayList<>();
        for (Object item : input) {
            switch (item) {
                case String itemStr -> result.add(replaceVariables(itemStr, environment));
                case Map<?,?> map -> result.add(replaceVariablesInMap((Map<String, Object>) item, environment));
                case List<?> list -> result.add(replaceVariablesInList(list, environment));
                case null, default -> result.add(item);
            }
        }
        return result;
    }

    public static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }

    public static String parseObjToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize DTO field to JSON", e);
            return "{}";
        }
    }

    public static String parseObjToString(Object obj) {
        if (obj instanceof String objStr) {
            return objStr;
        }
        return parseObjToJson(obj);
    }
}
