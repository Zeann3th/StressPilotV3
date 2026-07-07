package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.runs.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunMcpTools {

    private final RunService runService;

    @McpTool(description = "Get run history for a flow")
    public List<RunEntity> getAllRuns(
            @McpToolParam(description = "Flow ID") Long flowId) {
        return runService.getRunHistory(flowId);
    }

    @McpTool(description = "Get detailed information about a specific run")
    public RunEntity getRunDetail(
            @McpToolParam(description = "Run ID") String runId) {
        return runService.getRunDetail(runId);
    }

    @McpTool(description = "Get the latest run for a flow")
    public RunEntity getLastRun(
            @McpToolParam(description = "Flow ID") Long flowId) {
        return runService.getLastRun(flowId);
    }

    @McpTool(description = "Interrupt an ongoing test run")
    public void interruptRun(
            @McpToolParam(description = "Run ID") String runId) {
        runService.interruptRun(runId);
    }
}
