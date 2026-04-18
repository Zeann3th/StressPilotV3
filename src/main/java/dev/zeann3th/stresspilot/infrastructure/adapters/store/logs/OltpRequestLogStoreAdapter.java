package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.message.datasource", name = "type", havingValue = "primary", matchIfMissing = true)
public class OltpRequestLogStoreAdapter implements RequestLogStore {

    private final OltpRequestLogRepository repository;

    @Override
    public RequestLogEntity save(RequestLogEntity entity) { return repository.save(entity); }

    @Override
    public List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities) {
        return repository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true) public void streamLogsByRunId(String runId, Consumer<RequestLogEntity> consumer) {
        try (Stream<RequestLogEntity> stream = repository.streamAllByRunId(runId)) {
            stream.forEach(consumer);
        }
    }

    @Override
    @SuppressWarnings("java:S3776")
    public RunReport calculateRunReport(String runId, RunEntity run) {
        RunReport.RunReportBuilder builder = RunReport.builder().runId(runId);

        List<Object[]> summaryRows = repository.getRunSummaryStats(runId);
        if (!summaryRows.isEmpty() && summaryRows.getFirst()[0] != null) {
            Object[] row = summaryRows.getFirst();

            long totalReq = row[0] != null ? ((Number) row[0]).longValue() : 0L;
            long successCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            double avgResponse = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            LocalDateTime startTs = (LocalDateTime) row[3];
            LocalDateTime endTs = (LocalDateTime) row[4];

            double durationSec = deriveDuration(run, startTs, endTs);

            builder.totalRequests((int) totalReq)
                    .successCount((int) successCount)
                    .failureCount((int) (totalReq - successCount))
                    .successRate(totalReq == 0 ? 0.0 : round(100.0 * successCount / totalReq))
                    .failureRate(totalReq == 0 ? 0.0 : round(100.0 * (totalReq - successCount) / totalReq))
                    .avgResponse(round(avgResponse))
                    .durationSeconds(round(durationSec))
                    .tps(durationSec <= 0 ? 0.0 : round(totalReq / durationSec));
        }

        List<Long> times = repository.findResponseTimesByRunId(runId);
        Collections.sort(times);
        builder.p90(round(percentile(times, 90))).p95(round(percentile(times, 95)));

        List<Object[]> epStatsRaw = repository.getEndpointSummaryStats(runId);
        List<EndpointStats> endpointStats = epStatsRaw.stream().map(row -> {
            Long endpointId = (Long) row[0];
            String endpointName = (String) row[1];
            int requests = row[2] != null ? ((Number) row[2]).intValue() : 0;
            double avgMs = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

            return EndpointStats.builder()
                    .endpointId(endpointId)
                    .endpointName(endpointName != null ? endpointName : "Unknown")
                    .requests(requests)
                    .avgMs(round(avgMs))
                    .build();
        }).toList();

        Map<Long, List<Long>> epTimes = repository.findEndpointResponseTimesByRunId(runId).stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (Long) row[1], Collectors.toList())
                ));

        for (EndpointStats stat : endpointStats) {
            List<Long> eTimes = epTimes.getOrDefault(stat.getEndpointId(), Collections.emptyList());
            Collections.sort(eTimes);
            stat.setP90Ms(round(percentile(eTimes, 90)));
            stat.setP95Ms(round(percentile(eTimes, 95)));
        }

        builder.endpointStats(endpointStats);
        applyRunMetadata(builder, run);
        return builder.build();
    }

    private double percentile(List<Long> sorted, int p) {
        if (sorted == null || sorted.isEmpty()) return 0.0;
        int index = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private double deriveDuration(RunEntity run, LocalDateTime startTs, LocalDateTime endTs) {
        if (run != null && run.getStartedAt() != null && run.getCompletedAt() != null) {
            return Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis() / 1000.0;
        } else if (startTs != null && endTs != null) {
            return Duration.between(startTs, endTs).toMillis() / 1000.0;
        }
        return 1.0;
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private void applyRunMetadata(RunReport.RunReportBuilder builder, RunEntity run) {
        if (run != null) {
            try {
                if (run.getThreads() != null) builder.ccus(run.getThreads());
                if (run.getDuration() != null) builder.configuredDuration(run.getDuration());
                if (run.getRampUpDuration() != null) builder.rampUpTime(run.getRampUpDuration());
            } catch (Exception _) {
                // no-op
            }
        }
    }
}
