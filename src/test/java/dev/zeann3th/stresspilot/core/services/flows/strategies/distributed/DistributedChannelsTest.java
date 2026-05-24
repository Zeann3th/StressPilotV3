package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedChannelsTest {
    @Test
    void allChannelsAndKeysUseStresspilotPrefix() {
        DistributedChannels channels = new DistributedChannels("stresspilot");

        assertThat(channels.workerHeartbeatChannel()).isEqualTo("stresspilot:distributed:worker:heartbeat");
        assertThat(channels.workChannel()).isEqualTo("stresspilot:distributed:work");
        assertThat(channels.stopChannel()).isEqualTo("stresspilot:distributed:stop");
        assertThat(channels.requestLogChannel()).isEqualTo("stresspilot:distributed:request-log");
        assertThat(channels.workerKey("node-1")).isEqualTo("stresspilot:workers:node-1");
        assertThat(channels.runKey("run-1")).isEqualTo("stresspilot:runs:run-1");
    }
}
