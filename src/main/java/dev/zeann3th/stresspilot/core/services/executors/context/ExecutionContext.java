package dev.zeann3th.stresspilot.core.services.executors.context;

import java.util.function.Supplier;

public interface ExecutionContext {
    <T> T getState(Class<T> type, Supplier<T> factory);

    void clear();
}
