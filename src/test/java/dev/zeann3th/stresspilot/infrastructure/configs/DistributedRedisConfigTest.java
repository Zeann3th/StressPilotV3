package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedRedisConfigTest {
    @Test
    void distributedConfigDefaultsToDisabledAndStresspilotPrefix() {
        DistributedFlowProperties properties = new DistributedFlowProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getKeyPrefix()).isEqualTo("stresspilot");
        assertThat(properties.getWorkerTtlSeconds()).isEqualTo(15);
    }
}
