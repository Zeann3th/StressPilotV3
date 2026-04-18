package dev.zeann3th.stresspilot.core.services.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Slf4j(topic = "[MetricsPollerManager]")
@Component
@RequiredArgsConstructor
public class MetricsPollerManager {

    private static final long POLL_INTERVAL_SECONDS = 15;

    private final PrometheusCollectorScraper scraper;
    private final MetricLogService metricLogService;
    private final SimpMessagingTemplate ws;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeTasks
            = new ConcurrentHashMap<>();

    // small pool — metrics are lightweight, 2 threads cover many concurrent runs
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            2, Thread.ofVirtual().name("metrics-poller-", 0).factory());

    public void start(RunEntity run) {
        if (run.getMetricsEndpoint() == null || run.getMetricsEndpoint().isBlank()) {
            log.debug("Run {} — no metrics endpoint configured, skipping poller", run.getId());
            return;
        }
        log.info("Starting metrics poller for run {} → {}", run.getId(), run.getMetricsEndpoint());

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> collect(run),
                0,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        activeTasks.put(run.getId(), task);
    }

    public void stop(String runId) {
        ScheduledFuture<?> task = activeTasks.remove(runId);
        if (task == null) return;

        task.cancel(false);
        metricLogService.ensureFlushed();
        log.info("Metrics poller stopped for run {}", runId);
    }

    // ── internal ─────────────────────────────────────────────────────────────

    private void collect(RunEntity run) {
        try {
            MetricScrapeEventEntity event = scraper.scrape(run.getMetricsEndpoint(), run);
            if (event == null) return;

            // 1. WS push immediately — zero DB involvement, non-blocking
            ws.convertAndSend("/topic/metrics/" + run.getId(), event);

            // 2. queue for DB — goes through BatchMetricWriter, single-writer safe
            metricLogService.queueEvent(event);

        } catch (Exception e) {
            // never let a scrape error kill the scheduler thread
            log.warn("Metrics collection error for run {}: {}", run.getId(), e.getMessage());
        }
    }
}
