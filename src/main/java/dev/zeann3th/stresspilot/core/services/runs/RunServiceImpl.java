package dev.zeann3th.stresspilot.core.services.runs;

import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
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
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
    @Transactional(readOnly = true)
    public void exportRun(Long runId, HttpServletResponse response) {
        RunEntity run = runStore.findById(runId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0010));

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

    private RequestLog mapToDto(RequestLogEntity requestLogEntity) {
        Long id = (requestLogEntity.getEndpoint() != null) ? requestLogEntity.getEndpoint().getId() : null;
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
