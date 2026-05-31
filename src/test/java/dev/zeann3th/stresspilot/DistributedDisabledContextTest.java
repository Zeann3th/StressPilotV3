package dev.zeann3th.stresspilot;

import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedEventPublisher;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedFlowExecutor;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedMasterLogSubscriber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "application.distributed.enabled=false",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.ai.mcp.server.autoconfigure.McpWebMvcServerAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
})
@ActiveProfiles("test")
class DistributedDisabledContextTest {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextStartsWithDistributedModeDisabledAndNoRedis() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanNamesForType(DistributedEventPublisher.class)).hasSize(1);
        assertThat(applicationContext.getBeanNamesForType(DistributedFlowExecutor.class)).hasSize(1);
        assertThat(applicationContext.getBeanNamesForType(DistributedMasterLogSubscriber.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(DataRedisAutoConfiguration.class)).isEmpty();
    }
}
