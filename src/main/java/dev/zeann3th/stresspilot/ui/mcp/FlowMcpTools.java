package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FlowMcpTools {

    private final FlowService flowService;

    @McpTool(description = "List test flows with optional project and name filter")
    public Page<FlowEntity> listFlows(
            @McpToolParam(description = "Optional project ID filter") Long projectId,
            @McpToolParam(description = "Optional flow name filter") String name) {
        return flowService.getListFlow(projectId, name, PageRequest.of(0, 100));
    }

    @McpTool(description = "Get detailed information about a test flow")
    public FlowEntity getFlowDetail(
            @McpToolParam(description = "Flow ID") Long flowId) {
        return flowService.getFlowDetail(flowId);
    }

    @McpTool(description = "Create a new test flow. Example JSON: { \"projectId\": 1, \"name\": \"User Login Flow\", \"type\": \"DEFAULT\" }")
    public FlowEntity createFlow(
            @McpToolParam(description = "Creation command") CreateFlowCommand cmd) {
        return flowService.createFlow(cmd);
    }

    @McpTool(description = "Delete a test flow")
    public void deleteFlow(
            @McpToolParam(description = "Flow ID") Long flowId) {
        flowService.deleteFlow(flowId);
    }

    @McpTool(description = "Configure steps for a flow. Example JSON for steps: [ { \"type\": \"ENDPOINT\", \"endpointId\": 10 } ]")
    public void configureFlow(
            @McpToolParam(description = "Flow ID") Long flowId,
            @McpToolParam(description = "List of step commands") List<FlowStepCommand> steps) {
        flowService.configureFlow(flowId, steps);
    }

    @McpTool(description = "Run a test flow. Example JSON for RunFlowCommand: " +
            "{ \"environmentId\": 1, \"threads\": 10, \"totalDuration\": 60, \"rampUpDuration\": 5, \"variables\": { \"baseUrl\": \"http://...\" } }")
    public String runFlow(
            @McpToolParam(description = "Flow ID") Long flowId,
            @McpToolParam(description = "Run parameters") RunFlowCommand cmd) {
        return flowService.runFlow(flowId, cmd);
    }
}
