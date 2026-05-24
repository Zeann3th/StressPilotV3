package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedRedisConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DistributedFlowPropertiesConfig.class, DistributedRedisConfig.class);

    @Test
    void distributedConfigDefaultsToDisabledAndStresspilotPrefix() {
        DistributedFlowProperties properties = new DistributedFlowProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getKeyPrefix()).isEqualTo("stresspilot");
        assertThat(properties.getWorkerTtlSeconds()).isEqualTo(15);
    }

    @Test
    void distributedPropertiesAreAvailableWhenDistributedModeIsDisabled() {
        contextRunner
                .withPropertyValues("application.distributed.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedFlowProperties.class);
                    assertThat(context).doesNotHaveBean(DistributedRedisConfig.class);

                    DistributedFlowProperties properties = context.getBean(DistributedFlowProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getKeyPrefix()).isEqualTo("stresspilot");
                    assertThat(properties.getWorkerTtlSeconds()).isEqualTo(15);
                    assertThat(properties.getWorkerHeartbeatSeconds()).isEqualTo(5);
                    assertThat(properties.getWorkerDiscoveryTimeoutMs()).isEqualTo(2000);
                });
    }
}
