package dev.zeann3th.stresspilot.core.services.flows.nodes;

public record NodeHandlerResult(String nextId, Object outputData) {
    public static NodeHandlerResult of(String nextId) {
        return new NodeHandlerResult(nextId, null);
    }
}
