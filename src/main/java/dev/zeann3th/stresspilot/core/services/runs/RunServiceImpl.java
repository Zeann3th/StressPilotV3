package dev.zeann3th.stresspilot.core.services.runs;

import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunAnalysisDump;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunAnalysisMetadata;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.RunExportType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.events.InterruptRunEvent;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportSheetEntity;
import dev.zeann3th.stresspilot.core.ports.store.CustomReportSheetStore;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.utils.ExcelGenerator;
import dev.zeann3th.stresspilot.core.utils.report.ElementRenderContext;
import dev.zeann3th.stresspilot.core.utils.report.ReportElementRendererFactory;
import dev.zeann3th.stresspilot.core.utils.report.ReportTimeBucket;
import dev.zeann3th.stresspilot.core.utils.HtmlReportGenerator;
import dev.zeann3th.stresspilot.core.utils.RunComparisonExcelGenerator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class RunServiceImpl implements RunService {
    private static final Pattern ACTIVE_THREADS_PATTERN =
            Pattern.compile("__stresspilot_active_threads=(\\d+)");
    private static final Pattern ACTIVE_THREADS_JSON_PATTERN =
            Pattern.compile("\"__stresspilot_active_threads\"\\s*:\\s*(\\d+)");


    private final RunStore runStore;
    private final RequestLogStore requestLogStore;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomReportSheetStore customReportSheetStore;
    private final ReportElementRendererFactory reportElementRendererFactory;

    @Override
    public List<RunEntity> getRunHistory(Long flowId) {
        if (flowId == null) {
            return runStore.findAll();
        }
        return runStore.findAllByFlowId(flowId);
    }

    @Override
    public RunEntity getRunDetail(String runId) {
        return runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));
    }

    @Override
    public RunEntity getLastRun(Long flowId) {
        return runStore.findLastRunByFlowId(flowId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public RunAnalysisDump getRunAnalysisDump(String runId) {
        RunEntity run = getRunDetail(runId);
        RunReport report = requestLogStore.calculateRunReport(runId, run);
        List<RequestLog> logs = new ArrayList<>();
        requestLogStore.streamLogsByRunId(runId, entity -> logs.add(mapToDto(entity)));

        return RunAnalysisDump.builder()
                .run(mapRunMetadata(run))
                .report(report)
                .logCount(logs.size())
                .logs(logs)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void exportRun(String runId, RunExportType type, HttpServletResponse response) {
        RunEntity run = runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));

        if (RunStatus.RUNNING.name().equals(run.getStatus())) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0023);
        }

        RunReport report = requestLogStore.calculateRunReport(runId, run);

        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            if (type == RunExportType.HTML) {
                exportHtml(runId, response, report, now);
            } else {
                exportExcel(runId, response, report, now);
            }

        } catch (Exception e) {
            log.error("Failed to export run report for runId={}: {}", runId, e.getMessage(), e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER9999);
        }
    }

    private void exportExcel(String runId, HttpServletResponse response, RunReport report, String now) throws Exception {
        String rawFileName = "[Stress Pilot] _report_of_run_" + runId + "_" + now + ".xlsx";
        String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + rawFileName + "\"; filename*=UTF-8''" + encodedFileName);

        ExcelGenerator excel = new ExcelGenerator();
        excel.writeSummarySheets(report);
        excel.startDetailedSheet();

        // custom sheets
        List<CustomReportSheetEntity> customSheets = customReportSheetStore.findAll();
        List<RequestLog> allLogs = customSheets.isEmpty() ? null : new ArrayList<>();
        requestLogStore.streamLogsByRunId(runId, entity -> {
            RequestLog dto = mapToDto(entity);
            excel.appendDetailRow(dto);
            if (allLogs != null) allLogs.add(dto);
        });

        if (!customSheets.isEmpty() && allLogs != null) {
            List<ReportTimeBucket> timeBuckets = buildTimeBuckets(allLogs);
            ElementRenderContext renderCtx = ElementRenderContext.builder()
                    .report(report)
                    .logs(allLogs)
                    .timeBuckets(timeBuckets)
                    .build();
            excel.writeCustomSheets(customSheets, renderCtx, reportElementRendererFactory);
        }

        excel.export(response);
    }

    private List<ReportTimeBucket> buildTimeBuckets(List<RequestLog> logs) {
        if (logs.isEmpty()) return List.of();
        TreeMap<java.time.LocalDateTime, BucketAccumulator> buckets = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (RequestLog log : logs) {
            java.time.LocalDateTime ts = log.getCreatedAt() != null
                    ? log.getCreatedAt().withNano(0) : java.time.LocalDateTime.now().withNano(0);
            BucketAccumulator acc = buckets.computeIfAbsent(ts, ignored -> new BucketAccumulator());
            if (log.getResponseTime() != null) acc.responseTimes.add(log.getResponseTime().doubleValue());
            if (log.getActiveThreads() != null) acc.activeThreads = Math.max(acc.activeThreads, log.getActiveThreads());
            if (log.getResponse() != null) acc.bytes += log.getResponse().getBytes(StandardCharsets.UTF_8).length;
        }
        List<ReportTimeBucket> result = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            BucketAccumulator acc = entry.getValue();
            int count = acc.responseTimes.size();
            double avg = count == 0 ? 0 : acc.responseTimes.stream().mapToDouble(d -> d).average().orElse(0);
            Collections.sort(acc.responseTimes);
            double p90 = count == 0 ? 0 : acc.responseTimes.get((int) Math.ceil(count * 0.9) - 1);
            double p95 = count == 0 ? 0 : acc.responseTimes.get((int) Math.ceil(count * 0.95) - 1);
            result.add(new ReportTimeBucket(entry.getKey().format(fmt), avg, count, p90, p95,
                    acc.activeThreads, acc.bytes));
        }
        return result;
    }

    private static class BucketAccumulator {
        List<Double> responseTimes = new ArrayList<>();
        int activeThreads;
        long bytes;
    }

    private void exportHtml(String runId, HttpServletResponse response, RunReport report, String now) throws Exception {
        String rawFileName = "[Stress Pilot] _report_of_run_" + runId + "_" + now + ".html";
        String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

        response.setContentType("text/html;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + rawFileName + "\"; filename*=UTF-8''" + encodedFileName);

        HtmlReportGenerator html = new HtmlReportGenerator(report);
        int expectedLogCount = report.getTotalRequests() != null ? report.getTotalRequests() : 0;
        html.startDetailedLogs(expectedLogCount);
        requestLogStore.streamLogsByRunId(runId, entity -> html.appendLog(mapToDto(entity)));
        html.writeTo(response.getOutputStream());
    }

    @Override
    @Transactional(readOnly = true)
    public void exportRunComparison(String runId1, String runId2, HttpServletResponse response) {
        RunEntity run1 = getExportableRun(runId1);
        RunEntity run2 = getExportableRun(runId2);
        validateComparable(run1, run2);

        RunReport report1 = requestLogStore.calculateRunReport(runId1, run1);
        RunReport report2 = requestLogStore.calculateRunReport(runId2, run2);

        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String rawFileName = "[Stress Pilot] _comparison_report_" + runId1 + "_vs_" + runId2 + "_" + now + ".xlsx";
            String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + rawFileName + "\"; filename*=UTF-8''" + encodedFileName);

            RunComparisonExcelGenerator excel = new RunComparisonExcelGenerator(report1, report2);
            excel.writeComparisonSheets();
            excel.startRunDetailSheet("Run A Logs");
            requestLogStore.streamLogsByRunId(runId1, entity -> excel.appendRunDetailRow(mapToDto(entity)));
            excel.startRunDetailSheet("Run B Logs");
            requestLogStore.streamLogsByRunId(runId2, entity -> excel.appendRunDetailRow(mapToDto(entity)));
            excel.export(response);
        } catch (Exception e) {
            log.error("Failed to export comparison report for runId1={}, runId2={}: {}", runId1, runId2, e.getMessage(), e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER9999);
        }
    }

    private RunEntity getExportableRun(String runId) {
        RunEntity run = runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));
        if (RunStatus.RUNNING.name().equals(run.getStatus())) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0023);
        }
        return run;
    }

    private void validateComparable(RunEntity run1, RunEntity run2) {
        List<String> mismatches = new ArrayList<>();
        if (!Objects.equals(run1.getFlowId(), run2.getFlowId())) {
            mismatches.add("flow_id %s vs %s".formatted(run1.getFlowId(), run2.getFlowId()));
        }
        if (!Objects.equals(run1.getThreads(), run2.getThreads())) {
            mismatches.add("threads %s vs %s".formatted(run1.getThreads(), run2.getThreads()));
        }
        if (!Objects.equals(run1.getDuration(), run2.getDuration())) {
            mismatches.add("duration %s vs %s".formatted(run1.getDuration(), run2.getDuration()));
        }
        if (!Objects.equals(run1.getLoopCount(), run2.getLoopCount())) {
            mismatches.add("loop_count %s vs %s".formatted(run1.getLoopCount(), run2.getLoopCount()));
        }
        if (!Objects.equals(run1.getRampUpDuration(), run2.getRampUpDuration())) {
            mismatches.add("ramp_up_duration %s vs %s".formatted(run1.getRampUpDuration(), run2.getRampUpDuration()));
        }
        if (!mismatches.isEmpty()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                    Map.of(Constants.REASON, "Runs are not comparable: " + String.join(", ", mismatches)));
        }
    }

    @Override
    @Transactional
    public void interruptRun(String runId) {
        RunEntity run = runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));

        eventPublisher.publishEvent(new InterruptRunEvent(runId));

        LocalDateTime now = LocalDateTime.now();
        boolean durationMet = run.getStartedAt() != null && run.getDuration() != null
                && !now.isBefore(run.getStartedAt().plusSeconds(run.getDuration()));
        String finalStatus = durationMet ? RunStatus.COMPLETED.name() : RunStatus.ABORTED.name();

        int updated = runStore.finalizeRun(runId, finalStatus, now);
        if (updated == 0) {
            log.warn("Run {} could not be finalized - already finished", runId);
        }
    }

    private RunAnalysisMetadata mapRunMetadata(RunEntity run) {
        return RunAnalysisMetadata.builder()
                .id(run.getId())
                .flowId(run.getFlowId())
                .status(run.getStatus())
                .threads(run.getThreads())
                .duration(run.getDuration())
                .loopCount(run.getLoopCount())
                .rampUpDuration(run.getRampUpDuration())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }

    private RequestLog mapToDto(RequestLogEntity requestLogEntity) {
        Long id = requestLogEntity.getEndpointId();
        String name = (requestLogEntity.getEndpoint() != null) ? requestLogEntity.getEndpoint().getName() : null;

        return RequestLog.builder()
                .id(requestLogEntity.getId())
                .endpointId(id)
                .endpointName(name)
                .statusCode(requestLogEntity.getStatusCode())
                .success(requestLogEntity.getSuccess())
                .responseTime(requestLogEntity.getResponseTime())
                .correlationId(requestLogEntity.getCorrelationId())
                .activeThreads(extractActiveThreads(requestLogEntity.getRequest()))
                .request(requestLogEntity.getRequest())
                .response(requestLogEntity.getResponse())
                .createdAt(requestLogEntity.getCreatedAt())
                .build();
    }

    private Integer extractActiveThreads(String request) {
        if (request == null) {
            return null;
        }
        Matcher jsonMatcher = ACTIVE_THREADS_JSON_PATTERN.matcher(request);
        if (jsonMatcher.find()) {
            return Integer.valueOf(jsonMatcher.group(1));
        }
        Matcher matcher = ACTIVE_THREADS_PATTERN.matcher(request);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
