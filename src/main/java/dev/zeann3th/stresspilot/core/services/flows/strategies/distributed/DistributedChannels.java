package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

public record DistributedChannels(String prefix) {
    public String workerHeartbeatChannel() {
        return prefix + ":distributed:worker:heartbeat";
    }

    public String workChannel() {
        return prefix + ":distributed:work";
    }

    public String stopChannel() {
        return prefix + ":distributed:stop";
    }

    public String requestLogChannel() {
        return prefix + ":distributed:request-log";
    }

    public String workerKey(String nodeId) {
        return prefix + ":workers:" + nodeId;
    }

    public String runKey(String runId) {
        return prefix + ":runs:" + runId;
    }
}
