package dev.zeann3th.stresspilot.core.services.executors.context;

import org.pf4j.ExtensionPoint;

import java.util.function.Supplier;

public interface ExecutionContext extends ExtensionPoint {
    <T> T getState(Class<T> type, Supplier<T> factory);

    void clear();
}
