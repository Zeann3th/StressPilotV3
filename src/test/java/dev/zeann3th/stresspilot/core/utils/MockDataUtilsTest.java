package dev.zeann3th.stresspilot.core.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockDataUtilsTest {

    @Test
    void interpolatesSequencesTransformsAndUnknownCommands() {
        String first = MockDataUtils.interpolate("@{seq(orderTest, 10)}@");
        String second = MockDataUtils.interpolate("@{seq(orderTest, 10)}@");

        assertThat(List.of(
                first,
                second,
                MockDataUtils.interpolate("@{upper(lower(ABC))}@"),
                MockDataUtils.interpolate("@{not_real}@")))
                .containsExactly("10", "11", "ABC", "@{not_real}@");
    }

    @Test
    void interpolatesUuidAndNestedCollections() {
        String uuid = MockDataUtils.interpolate("@{uuid}@");
        assertThat(UUID.fromString(uuid)).hasToString(uuid);

        Map<String, Object> map = MockDataUtils.interpolateInMap(Map.of(
                "id", "order-@{seq(mapOrder, 1)}@",
                "items", List.of("@{lower(SKU)}@")));

        assertThat(map)
                .containsEntry("id", "order-1")
                .containsEntry("items", List.of("sku"));
    }

    @Test
    void nullCollectionsReturnEmptyCollections() {
        assertThat(List.of(
                MockDataUtils.interpolateInMap(null).isEmpty(),
                MockDataUtils.interpolateInList(null).isEmpty()))
                .containsOnly(true);
    }
}
