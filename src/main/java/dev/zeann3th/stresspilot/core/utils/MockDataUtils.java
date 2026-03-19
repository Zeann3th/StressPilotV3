package dev.zeann3th.stresspilot.core.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j(topic = "MockDataUtils")
@UtilityClass
public class MockDataUtils {
    private static final Pattern MOCK_PATTERN = Pattern.compile("@\\{\\s*(.+?)\\s*\\}@");
    private static final int MAX_DEPTH = 5;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private static final Faker FAKER = new Faker();

    private static final Map<String, AtomicLong> SEQUENCES = new ConcurrentHashMap<>();

    public static String interpolate(String input) {
        return interpolate(input, 0);
    }

    private static String interpolate(String input, int depth) {
        if (input == null || !input.contains("@{")) return input;

        if (depth > MAX_DEPTH) {
            log.warn("Max recursion depth ({}) reached for mock interpolation. Returning current state.", MAX_DEPTH);
            return input;
        }

        Matcher matcher = MOCK_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String command = matcher.group(1).trim();
            String replacement = resolveCommand(command);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();

        if (result.equals(input)) {
            return result;
        }

        return result.contains("@{") ? interpolate(result, depth + 1) : result;
    }

    public static Map<String, Object> interpolateInMap(Map<String, Object> input) {
        if (input == null) return Collections.emptyMap();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            result.put(entry.getKey(), interpolateObject(entry.getValue()));
        }
        return result;
    }

    public static List<Object> interpolateInList(List<?> input) {
        if (input == null) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (Object item : input) {
            result.add(interpolateObject(item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object interpolateObject(Object obj) {
        return switch (obj) {
            case String s -> interpolate(s);
            case Map<?, ?> m -> interpolateInMap((Map<String, Object>) m);
            case List<?> l -> interpolateInList(l);
            case null, default -> obj;
        };
    }

    private static String resolveCommand(String command) {
        if (command.startsWith("upper(")) return handleTransform(command, String::toUpperCase);
        if (command.startsWith("lower(")) return handleTransform(command, String::toLowerCase);

        if (command.startsWith("faker(")) return handleFaker(command);

        if (command.startsWith("seq(")) {
            String param = extractParam(command);
            String[] parts = param.split(",", 2);
            String name = parts[0].trim();
            long start = parts.length > 1 ? Long.parseLong(parts[1].trim()) - 1 : 0;
            return String.valueOf(SEQUENCES.computeIfAbsent(name, _ -> new AtomicLong(start)).incrementAndGet());
        }

        if (command.startsWith("pick(")) {
            String param = extractParam(command);
            String[] values = param.split(",");
            if (values.length == 0) return "@{" + command + "}@";
            return values[FAKER.random().nextInt(values.length)].trim();
        }

        if (command.equalsIgnoreCase("timestamp")) return String.valueOf(System.currentTimeMillis());
        if (command.equalsIgnoreCase("date")) return Instant.now().toString();
        if (command.equalsIgnoreCase("datetime")) return LocalDateTime.now().format(DATETIME_FORMATTER);

        if (command.equalsIgnoreCase("uuid")) return UUID.randomUUID().toString();

        return "@{" + command + "}@";
    }

    private static String handleFaker(String command) {
        String expression = extractParam(command);
        try {
            return FAKER.expression("#{" + expression + "}");
        } catch (Exception _) {
            log.warn("Invalid faker expression: '{}'. Returning original command.", expression);
            return "@{" + command + "}@";
        }
    }

    private static String handleTransform(String command, UnaryOperator<String> transformer) {
        String inner = extractParam(command);

        String resolvedInner = interpolate(inner);

        if (!resolvedInner.contains("@{")) {
            String potentialResolution = resolveCommand(resolvedInner);
            if (!potentialResolution.startsWith("@{")) {
                resolvedInner = potentialResolution;
            }
        }

        return transformer.apply(resolvedInner);
    }

    private static String extractParam(String command) {
        int start = command.indexOf("(") + 1;
        int end = command.lastIndexOf(")");
        return (start > 0 && end > start) ? command.substring(start, end) : command;
    }
}