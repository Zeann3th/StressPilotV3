package dev.zeann3th.stresspilot.core.services.runs;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunSnapshotEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.events.InterruptRunEvent;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.core.ports.store.RunSnapshotStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.utils.ExcelGenerator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class RunServiceImpl implements RunService {

    private final RunStore runStore;
    private final RequestLogStore requestLogStore;
    private final RunSnapshotStore runSnapshotStore;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

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
    public void exportRun(String runId, HttpServletResponse response) {
        RunEntity run = runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));

        if (RunStatus.RUNNING.name().equals(run.getStatus())) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0023);
        }

        RunReport report = requestLogStore.calculateRunReport(runId, run);

        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String rawFileName = "[Stress Pilot] _report_of_run_" + runId + "_" + now + ".xlsx";
            String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + rawFileName + "\"; filename*=UTF-8''" + encodedFileName);

            ExcelGenerator excel = new ExcelGenerator();

            excel.writeSummarySheets(report);

            excel.startDetailedSheet();

            requestLogStore.streamLogsByRunId(runId, entity -> excel.appendDetailRow(mapToDto(entity)));

            excel.export(response);

        } catch (Exception e) {
            log.error("Failed to export run report for runId={}: {}", runId, e.getMessage(), e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER9999);
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

    @Override
    @Transactional
    public void performSnapshotting() {
        List<RunEntity> runs = runStore.findCompletedWithoutSnapshot(10);
        for (RunEntity run : runs) {
            try {
                if (!runSnapshotStore.existsById(run.getId())) {
                    createSnapshot(run);
                }
            } catch (Exception e) {
                log.error("Failed to auto-create snapshot for run {}: {}", run.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public RunSnapshotEntity createManualSnapshot(String runId) {
        RunEntity run = runStore.findById(runId).orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));
        if (!RunStatus.COMPLETED.name().equals(run.getStatus())) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0001, Map.of(Constants.REASON, "Only completed runs can be snapshotted"));
        }
        if (runSnapshotStore.existsById(runId)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0027);
        }
        return createSnapshot(run);
    }

    @Override
    @Transactional(readOnly = true)
    public RunSnapshotEntity getRunSnapshot(String runId) {
        return runSnapshotStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunSnapshotEntity> compareSnapshots(String runId1, String runId2) {
        return List.of(getRunSnapshot(runId1), getRunSnapshot(runId2));
    }

    private RunSnapshotEntity createSnapshot(RunEntity run) {
        if (run.getStartedAt() == null || run.getCompletedAt() == null) {
            log.warn("Run {} has missing timestamps, skipping snapshot", run.getId());
            return null;
        }

        long totalDurationMs = Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis();
        double binWidthMs = Math.max(1.0, totalDurationMs / 20.0);

        Map<Long, Map<Integer, List<Long>>> groupedLogs = new HashMap<>();

        requestLogStore.streamLogsByRunId(run.getId(), logEntity -> {
            Long endpointId = logEntity.getEndpointId();
            long offsetMs = Duration.between(run.getStartedAt(), logEntity.getCreatedAt()).toMillis();
            int binIndex = (int) (offsetMs / binWidthMs);
            if (binIndex < 0) binIndex = 0;
            if (binIndex > 19) binIndex = 19;

            groupedLogs.computeIfAbsent(endpointId, k -> new HashMap<>())
                    .computeIfAbsent(binIndex, k -> new ArrayList<>())
                    .add(logEntity.getResponseTime());
        });

        Map<Long, List<Map<String, Object>>> finalMetrics = new HashMap<>();

        for (Long endpointId : groupedLogs.keySet()) {
            List<Map<String, Object>> bins = new ArrayList<>();
            Map<Integer, List<Long>> endpointBins = groupedLogs.get(endpointId);

            for (int i = 0; i < 20; i++) {
                List<Long> latencies = endpointBins.getOrDefault(i, Collections.emptyList());
                double avg = latencies.isEmpty() ? 0.0 : latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
                int count = latencies.size();

                Map<String, Object> bin = new HashMap<>();
                bin.put("bin_index", i);
                bin.put("avg_response_time", avg);
                bin.put("request_count", count);
                bins.add(bin);
            }
            finalMetrics.put(endpointId, bins);
        }

        try {
            String metricsJson = objectMapper.writeValueAsString(finalMetrics);

            RunSnapshotEntity snapshot = RunSnapshotEntity.builder()
                    .id(run.getId())
                    .flowId(run.getFlowId())
                    .status(run.getStatus())
                    .threads(run.getThreads())
                    .duration(run.getDuration())
                    .rampUpDuration(run.getRampUpDuration())
                    .startedAt(run.getStartedAt())
                    .completedAt(run.getCompletedAt())
                    .metrics(metricsJson)
                    .createdAt(LocalDateTime.now())
                    .build();

            log.info("Saving snapshot for run {}", run.getId());
            return runSnapshotStore.save(snapshot);
        } catch (Exception e) {
            log.error("Failed to create snapshot for run {}: {}", run.getId(), e.getMessage());
            throw CommandExceptionBuilder.exception(ErrorCode.ER9999);
        }
    }

    private RequestLog mapToDto(RequestLogEntity requestLogEntity) {
        Long id = requestLogEntity.getEndpointId();
        String name = (requestLogEntity.getEndpoint() != null) ? requestLogEntity.getEndpoint().getName() : null;

        return RequestLog.builder()
                .id(requestLogEntity.getId())
                .endpointId(id)
                .endpointName(name)
                .statusCode(requestLogEntity.getStatusCode())
                .responseTime(requestLogEntity.getResponseTime())
                .request(requestLogEntity.getRequest())
                .response(requestLogEntity.getResponse())
                .createdAt(requestLogEntity.getCreatedAt())
                .build();
    }
}
