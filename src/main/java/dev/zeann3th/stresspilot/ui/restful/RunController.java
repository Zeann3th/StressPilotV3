package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.runs.RunService;
import dev.zeann3th.stresspilot.ui.restful.dtos.run.RunResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.RunMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@ResponseWrapper
public class RunController {

    private final RunService runService;
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
        RunEntity resp = runService.getLastRun(flowId);
        return runMapper.toResponse(resp);
    }

    @GetMapping("/{runId}/export")
    @Operation(summary = "Export Run Report", description = "Downloads the run report as an Excel file.", responses = {
            @ApiResponse(responseCode = "200", description = "Excel file downloaded successfully", content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @Schema(type = "string", format = "binary")))
    })
    public void exportRun(
            @PathVariable Long runId,
            HttpServletResponse response) {
        runService.exportRun(runId, response);
    }
}
