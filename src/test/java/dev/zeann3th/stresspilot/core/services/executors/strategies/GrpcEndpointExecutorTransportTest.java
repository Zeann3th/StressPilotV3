package dev.zeann3th.stresspilot.core.services.executors.strategies;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcEndpointExecutorTransportTest {

    @Test
    void usesSecureTransportOnlyWhenStressPilotSecureHeaderIsTrue() {
        assertThat(GrpcEndpointExecutor.usesSecureTransport(Map.of(
                "X-StressPilot-Secure", "true"
        ))).isTrue();

        assertThat(GrpcEndpointExecutor.usesSecureTransport(Map.of(
                "x-stresspilot-secure", " TRUE "
        ))).isTrue();

        assertThat(GrpcEndpointExecutor.usesSecureTransport(Map.of(
                "X-StressPilot-Secure", "false"
        ))).isFalse();

        assertThat(GrpcEndpointExecutor.usesSecureTransport(Map.of(
                "Content-Type", "application/json"
        ))).isFalse();
    }

    @Test
    void channelCacheKeyIncludesTransportMode() {
        String target = "localhost:50051";

        assertThat(GrpcEndpointExecutor.channelCacheKey(target, true))
                .isEqualTo("secure|localhost:50051");
        assertThat(GrpcEndpointExecutor.channelCacheKey(target, false))
                .isEqualTo("plaintext|localhost:50051");
    }
}
