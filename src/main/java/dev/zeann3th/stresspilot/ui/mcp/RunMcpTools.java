package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.runs.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunMcpTools {

    private final RunService runService;

    @Tool(description = "Get run history for a flow")
    public List<RunEntity> getAllRuns(
            @ToolParam(description = "Flow ID") Long flowId) {
        return runService.getRunHistory(flowId);
    }

    @Tool(description = "Get detailed information about a specific run")
    public RunEntity getRunDetail(
            @ToolParam(description = "Run ID") String runId) {
        return runService.getRunDetail(runId);
    }

    @Tool(description = "Get the latest run for a flow")
    public RunEntity getLastRun(
            @ToolParam(description = "Flow ID") Long flowId) {
        return runService.getLastRun(flowId);
    }

    @Tool(description = "Interrupt an ongoing test run")
    public void interruptRun(
            @ToolParam(description = "Run ID") String runId) {
        runService.interruptRun(runId);
    }
}
