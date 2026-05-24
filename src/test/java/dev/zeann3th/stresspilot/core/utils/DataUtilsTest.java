package dev.zeann3th.stresspilot.core.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataUtilsTest {

    @Test
    void replacesVariablesAndLeavesUnknownPlaceholdersUnchanged() {
        String result = DataUtils.replaceVariables(
                "Bearer {{ token }} for {{ missing }}",
                Map.of("token", "abc123"));

        assertThat(result).isEqualTo("Bearer abc123 for {{ missing }}");
    }

    @Test
    void replacesVariablesRecursivelyInMapsAndLists() {
        Map<String, Object> input = Map.of(
                "url", "https://{{ host }}/orders",
                "headers", Map.of("Authorization", "Bearer {{ token }}"),
                "items", List.of("{{ sku }}", Map.of("qty", "{{ quantity }}")));

        Map<String, Object> result = DataUtils.replaceVariablesInMap(input, Map.of(
                "host", "api.example.test",
                "token", "secret",
                "sku", "A-1",
                "quantity", 3));

        assertThat(result)
                .containsEntry("url", "https://api.example.test/orders")
                .containsEntry("headers", Map.of("Authorization", "Bearer secret"))
                .containsEntry("items", List.of("A-1", Map.of("qty", "3")));
    }

    @Test
    void flattensNestedObjectsIntoDotPaths() {
        List<Map.Entry<String, Object>> out = new ArrayList<>();

        DataUtils.flattenObject(Map.of(
                "user", Map.of("name", "Ana"),
                "roles", List.of("admin", "tester")), "", out);

        assertThat(out)
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("user.name", "roles.0", "roles.1");
    }

    @Test
    void serializesObjectsAndPreservesStrings() {
        assertThat(DataUtils.parseObjToString("raw")).isEqualTo("raw");
        assertThat(DataUtils.parseObjToJson(Map.of("ok", true))).contains("\"ok\":true");
        assertThat(DataUtils.hasText("  value ")).isTrue();
        assertThat(DataUtils.hasText("   ")).isFalse();
    }
}
