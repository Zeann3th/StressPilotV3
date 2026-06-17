package dev.zeann3th.stresspilot.core.utils.report;

public record ReportTimeBucket(
    String timeLabel,
    double avgMs,
    double rps,
    double p90Ms,
    double p95Ms,
    int activeThreads,
    long responseBytes
) {}
