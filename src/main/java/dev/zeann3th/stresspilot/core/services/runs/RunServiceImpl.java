package dev.zeann3th.stresspilot.core.services.runs;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.utils.ExcelGenerator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class RunServiceImpl implements RunService {

    private final RunStore runStore;
    private final RequestLogStore requestLogStore;

    @Override
    public List<RunEntity> getRunHistory(Long flowId) {
        return runStore.findAllByFlowId(flowId);
    }

    @Override
    public RunEntity getRunDetail(Long runId) {
        return runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));
    }

    @Override
    public RunEntity getLastRun(Long flowId) {
        return runStore.findLastRunByFlowId(flowId).orElse(null);
    }

    @Override
    public RunReport generateReport(Long runId) {
        RunEntity run = runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));
        List<RequestLogEntity> logs = requestLogStore.findAllByRunId(runId);
        return buildReport(runId, run, logs);
    }

    @Override
    public void exportRun(Long runId, String type, HttpServletResponse response) {
        String reportTypeStr = (type == null || type.isEmpty()) ? "DETAILED" : type.toUpperCase();

        if (!"DETAILED".equals(reportTypeStr) && !"SUMMARY".equals(reportTypeStr)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0022, Map.of(Constants.TYPE, reportTypeStr));
        }

        RunReport report = generateReport(runId);

        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String rawFileName = "[Stress Pilot] " + reportTypeStr + "_report_of_run_" + runId + "_" + now + ".xlsx";
            String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + rawFileName + "\"; filename*=UTF-8''" + encodedFileName);

            new ExcelGenerator<>()
                    .writeRunReport(report)
                    .export(response);

        } catch (Exception e) {
            log.error("Failed to export run report for runId={}: {}", runId, e.getMessage(), e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER9999);
        }
    }

    private RunReport buildReport(Long runId, RunEntity run, List<RequestLogEntity> logs) {
        List<Long> responseTimes = logs.stream()
                .map(RequestLogEntity::getResponseTime)
                .filter(Objects::nonNull)
                .map(Number::longValue)
                .sorted()
                .toList();

        int totalRequests = logs.size();
        int successCount = (int) logs.stream().filter(r -> {
            if (r.getSuccess() != null)
                return r.getSuccess();
            Integer sc = r.getStatusCode();
            return sc != null && sc >= 200 && sc < 300;
        }).count();
        int failureCount = totalRequests - successCount;
        double successRate = totalRequests == 0 ? 0.0 : 100.0 * successCount / totalRequests;
        double failureRate = totalRequests == 0 ? 0.0 : 100.0 * failureCount / totalRequests;

        double avgResponse = responseTimes.isEmpty() ? 0.0
                : responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double p90 = percentile(responseTimes, 90);
        double p95 = percentile(responseTimes, 95);

        double durationSeconds = deriveDurationSeconds(run, logs);
        double tps = durationSeconds <= 0 ? 0.0 : totalRequests / durationSeconds;

        java.util.Map<Long, String> endpointNameMap = new java.util.HashMap<>();
        for (RequestLogEntity r : logs) {
            if (r.getEndpoint() != null && r.getEndpoint().getId() != null) {
                endpointNameMap.putIfAbsent(r.getEndpoint().getId(), r.getEndpoint().getName());
            }
        }

        java.util.Map<Long, java.util.List<Long>> perEndpoint = new java.util.HashMap<>();
        for (RequestLogEntity r : logs) {
            Long endpointId = (r.getEndpoint() == null || r.getEndpoint().getId() == null) ? -1L
                    : r.getEndpoint().getId();
            if (r.getResponseTime() != null) {
                perEndpoint.computeIfAbsent(endpointId, _ -> new java.util.ArrayList<>()).add(r.getResponseTime());
            }
        }

        List<dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats> endpointStats = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Long, java.util.List<Long>> e : perEndpoint.entrySet()) {
            List<Long> times = e.getValue().stream().sorted().toList();
            double avg = times.isEmpty() ? 0.0 : times.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double p90e = percentile(times, 90);
            double p95e = percentile(times, 95);
            endpointStats.add(dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats.builder()
                    .endpointId(e.getKey())
                    .endpointName(endpointNameMap.getOrDefault(e.getKey(), "Unknown endpoint"))
                    .requests(times.size())
                    .avgMs(avg)
                    .p90Ms(p90e)
                    .p95Ms(p95e)
                    .build());
        }

        List<dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog> details = new java.util.ArrayList<>();
        for (RequestLogEntity l : logs) {
            Long eid = (l.getEndpoint() != null) ? l.getEndpoint().getId() : null;
            details.add(dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog.builder()
                    .id(l.getId())
                    .endpointId(eid)
                    .endpointName(eid != null ? endpointNameMap.getOrDefault(eid, "Unknown endpoint") : null)
                    .statusCode(l.getStatusCode())
                    .responseTime(l.getResponseTime())
                    .request(l.getRequest())
                    .response(l.getResponse())
                    .createdAt(l.getCreatedAt())
                    .build());
        }

        RunReport.RunReportBuilder builder = RunReport.builder()
                .runId(runId)
                .totalRequests(totalRequests)
                .successCount(successCount)
                .failureCount(failureCount)
                .successRate(round(successRate))
                .failureRate(round(failureRate))
                .avgResponse(round(avgResponse))
                .p90(round(p90))
                .p95(round(p95))
                .durationSeconds(round(durationSeconds))
                .tps(round(tps))
                .endpointStats(endpointStats)
                .details(details);

        if (run != null) {
            try {
                if (run.getThreads() != null)
                    builder.ccus(run.getThreads());
                if (run.getDuration() != null)
                    builder.configuredDuration(run.getDuration());
                if (run.getRampUpDuration() != null)
                    builder.rampUpTime(run.getRampUpDuration());
            } catch (Exception _) {
                // no-op
            }
        }

        return builder.build();
    }

    private static double percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues == null || sortedValues.isEmpty())
            return 0.0;
        int n = sortedValues.size();
        double rank = percentile / 100.0 * (n - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (upper >= n)
            return sortedValues.get(n - 1);
        if (lower == upper)
            return sortedValues.get(lower);
        double weight = rank - lower;
        return sortedValues.get(lower) * (1 - weight) + sortedValues.get(upper) * weight;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double deriveDurationSeconds(RunEntity run, List<RequestLogEntity> logs) {
        if (run != null && run.getStartedAt() != null && run.getCompletedAt() != null) {
            return Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis() / 1000.0;
        }

        LocalDateTime min = null;
        LocalDateTime max = null;
        for (RequestLogEntity l : logs) {
            LocalDateTime ts = l.getCreatedAt();
            if (ts != null) {
                if (min == null || ts.isBefore(min))
                    min = ts;
                if (max == null || ts.isAfter(max))
                    max = ts;
            }
        }
        if (min != null && max != null) {
            return Duration.between(min, max).toMillis() / 1000.0;
        }
        return Math.max(1.0, !logs.isEmpty() ? 1.0 : 0.0);
    }
}
