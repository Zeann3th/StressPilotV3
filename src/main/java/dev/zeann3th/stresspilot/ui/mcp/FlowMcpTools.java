package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FlowMcpTools {

    private final FlowService flowService;

    @Tool(description = "List test flows with optional project and name filter")
    public Page<FlowEntity> listFlows(
            @ToolParam(description = "Optional project ID filter") Long projectId,
            @ToolParam(description = "Optional flow name filter") String name) {
        return flowService.getListFlow(projectId, name, PageRequest.of(0, 100));
    }

    @Tool(description = "Get detailed information about a test flow")
    public FlowEntity getFlowDetail(
            @ToolParam(description = "Flow ID") Long flowId) {
        return flowService.getFlowDetail(flowId);
    }

    @Tool(description = "Create a new test flow. Example JSON: { \"projectId\": 1, \"name\": \"User Login Flow\", \"type\": \"DEFAULT\" }")
    public FlowEntity createFlow(
            @ToolParam(description = "Creation command") CreateFlowCommand cmd) {
        return flowService.createFlow(cmd);
    }

    @Tool(description = "Delete a test flow")
    public void deleteFlow(
            @ToolParam(description = "Flow ID") Long flowId) {
        flowService.deleteFlow(flowId);
    }

    @Tool(description = "Configure steps for a flow. Example JSON for steps: [ { \"type\": \"ENDPOINT\", \"endpointId\": 10 } ]")
    public void configureFlow(
            @ToolParam(description = "Flow ID") Long flowId,
            @ToolParam(description = "List of step commands") List<FlowStepCommand> steps) {
        flowService.configureFlow(flowId, steps);
    }

    @Tool(description = "Run a test flow. Example JSON for RunFlowCommand: " +
            "{ \"environmentId\": 1, \"threads\": 10, \"totalDuration\": 60, \"rampUpDuration\": 5, \"variables\": { \"baseUrl\": \"http://...\" } }")
    public String runFlow(
            @ToolParam(description = "Flow ID") Long flowId,
            @ToolParam(description = "Run parameters") RunFlowCommand cmd) {
        return flowService.runFlow(flowId, cmd);
    }
}
