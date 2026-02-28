package dev.zeann3th.stresspilot.core.services.executors.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class BaseExecutionContext implements ExecutionContext{
    private final Map<Class<?>, Object> states = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getState(Class<T> type, Supplier<T> factory) {
        return (T) states.computeIfAbsent(type, _ -> factory.get());
    }

    @Override
    public void clear() {
        states.clear();
    }
}
