package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedRedisConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DistributedFlowPropertiesConfig.class, DistributedRedisConfig.class);

    @Test
    void distributedConfigDefaultsToDisabledAndStresspilotPrefix() {
        DistributedFlowProperties properties = new DistributedFlowProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getKeyPrefix()).isEqualTo("stresspilot");
        assertThat(properties.getNodeId()).isEqualTo(DistributedFlowProperties.resolveDefaultNodeId(System.getenv()));
        assertThat(properties.getNodeId()).isNotBlank();
        assertThat(properties.getWorkerTtlSeconds()).isEqualTo(15);
    }

    @Test
    void nodeIdDefaultFallsBackFromHostNameToComputerNameToLocal() {
        assertThat(DistributedFlowProperties.resolveDefaultNodeId(Map.of(
                "HOSTNAME", "stresspilot-host",
                "COMPUTERNAME", "stresspilot-computer"
        ))).isEqualTo("stresspilot-host");
        assertThat(DistributedFlowProperties.resolveDefaultNodeId(Map.of(
                "COMPUTERNAME", "stresspilot-computer"
        ))).isEqualTo("stresspilot-computer");
        assertThat(DistributedFlowProperties.resolveDefaultNodeId(Map.of())).isEqualTo("local");
    }

    @Test
    void applicationYamlDisablesRedisHealthByDefault() throws Exception {
        Object redisHealthEnabled = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"))
                .stream()
                .map(propertySource -> propertySource.getProperty("management.health.redis.enabled"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertThat(redisHealthEnabled).isEqualTo(false);
    }

    @Test
    void applicationYamlExcludesRedisAutoConfigurationByDefault() throws Exception {
        List<Object> excludedAutoConfigurations = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"))
                .stream()
                .map(propertySource -> propertySource.getProperty("spring.autoconfigure.exclude[0]"))
                .filter(Objects::nonNull)
                .toList();

        assertThat(excludedAutoConfigurations)
                .contains(DataRedisAutoConfiguration.class.getName());
    }

    @Test
    void redisRuntimeBeansAreNotCreatedWhenDistributedModeIsDisabled() {
        contextRunner
                .withPropertyValues("application.distributed.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
                    assertThat(context).doesNotHaveBean(StringRedisTemplate.class);
                });
    }

    @Test
    void redisRuntimeBeansAreCreatedWhenDistributedModeIsEnabled() {
        contextRunner
                .withPropertyValues("application.distributed.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                });
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
