package dev.zeann3th.stresspilot.core.utils;

import lombok.Data;
import okhttp3.CookieJar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class FlowThreadContext {
    private int threadId;
    private Long runId;
    private CookieJar cookieJar;
    private int iterationCount;
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    public void incrementIteration() {
        this.iterationCount++;
    }
}
