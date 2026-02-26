package dev.zeann3th.stresspilot.ui.restful.controllers;

import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.runs.RunService;
import dev.zeann3th.stresspilot.infrastructure.report.ExcelGenerator;
import dev.zeann3th.stresspilot.ui.restful.dtos.run.RunResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.RunMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@ResponseWrapper
public class RunController {

    private final RunService runService;
    private final dev.zeann3th.stresspilot.core.services.flows.FlowService flowService;
    private final RunMapper runMapper;

    @GetMapping
    public List<RunResponseDTO> getAllRuns(
            @RequestParam(value = "flowId", required = false) Long flowId) {
        List<RunEntity> resp = runService.getRunHistory(flowId);
        return resp.stream().map(runMapper::toResponse).toList();
    }

    @GetMapping("/{runId}")
    public RunResponseDTO getRunDetail(@PathVariable Long runId) {
        RunEntity resp = runService.getRunDetail(runId);
        return runMapper.toResponse(resp);
    }

    @GetMapping("/last")
    public RunResponseDTO getLastRun(@RequestParam("flowId") Long flowId) {
        RunEntity resp = flowService.getLastRun(flowId);
        return runMapper.toResponse(resp);
    }

    @GetMapping("/{runId}/export")
    @Operation(summary = "Export Run Report", description = "Downloads the run report as an Excel file.", responses = {
            @ApiResponse(responseCode = "200", description = "Excel file downloaded successfully", content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @Schema(type = "string", format = "binary")))
    })
    public void exportRun(
            @PathVariable Long runId,
            @RequestParam(value = "type", required = false) String type,
            HttpServletResponse response) {
        try {
            RunReport report = runService.generateReport(runId);

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String reportTypeStr = (type == null || type.isEmpty()) ? "DETAILED" : type.toUpperCase();

            if (!"DETAILED".equals(reportTypeStr) && !"SUMMARY".equals(reportTypeStr)) {
                throw CommandExceptionBuilder.exception(ErrorCode.SP0000); // Bad request equivalent
            }

            String rawFileName = "[Stress Pilot] " + reportTypeStr + "_report_of_run_" + runId + "_" + now + ".xlsx";
            String encodedFileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String contentDisposition = "attachment; filename=\"" + rawFileName + "\"; filename*=UTF-8''"
                    + encodedFileName;
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);

            new ExcelGenerator<>()
                    .writeRunReport(report)
                    .export(response);

        } catch (Exception e) {
            throw CommandExceptionBuilder.exception(ErrorCode.SP0000); // Fallback generic exception
        }
    }
}
