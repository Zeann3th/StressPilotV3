package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.services.runs.RunService;
import dev.zeann3th.stresspilot.ui.restful.dtos.run.RunResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.RunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunMcpTools {

    private final RunService runService;
    private final RunMapper runMapper;

    @McpTool(description = "Get run history for a flow", generateOutputSchema = true)
    public List<RunResponseDTO> getAllRuns(
            @McpToolParam(description = "Flow ID") Long flowId) {
        return runService.getRunHistory(flowId).stream()
                .map(runMapper::toResponse)
                .toList();
    }

    @McpTool(description = "Get detailed information about a specific run", generateOutputSchema = true)
    public RunResponseDTO getRunDetail(
            @McpToolParam(description = "Run ID") String runId) {
        return runMapper.toResponse(runService.getRunDetail(runId));
    }

    @McpTool(description = "Get the latest run for a flow", generateOutputSchema = true)
    public RunResponseDTO getLastRun(
            @McpToolParam(description = "Flow ID") Long flowId) {
        return runMapper.toResponse(runService.getLastRun(flowId));
    }

    @McpTool(description = "Interrupt an ongoing test run")
    public void interruptRun(
            @McpToolParam(description = "Run ID") String runId) {
        runService.interruptRun(runId);
    }
}
