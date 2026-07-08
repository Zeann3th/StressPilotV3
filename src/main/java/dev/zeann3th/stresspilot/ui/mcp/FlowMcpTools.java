package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.DryRunStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.DryRunStepResult;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.FlowResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.FlowMapper;
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
    private final FlowMapper flowMapper;

    @McpTool(description = "List test flows with optional project and name filter", generateOutputSchema = true)
    public Page<FlowResponseDTO> listFlows(
            @McpToolParam(description = "Optional project ID filter") Long projectId,
            @McpToolParam(description = "Optional flow name filter") String name) {
        return flowService.getListFlow(projectId, name, PageRequest.of(0, 100))
                .map(flowMapper::toResponse);
    }

    @McpTool(description = "Get detailed information about a test flow", generateOutputSchema = true)
    public FlowResponseDTO getFlowDetail(
            @McpToolParam(description = "Flow ID") Long flowId) {
        return flowMapper.toResponse(flowService.getFlowDetail(flowId));
    }

    @McpTool(description = "Create a new test flow. Example JSON: { \"projectId\": 1, \"name\": \"User Login Flow\", \"type\": \"DEFAULT\" }", generateOutputSchema = true)
    public FlowResponseDTO createFlow(
            @McpToolParam(description = "Creation command") CreateFlowCommand cmd) {
        return flowMapper.toResponse(flowService.createFlow(cmd));
    }

    @McpTool(description = "Update an existing flow. Patch fields match flow JSON properties, for example { \"name\": \"Checkout Flow\", \"type\": \"DEFAULT\" }", generateOutputSchema = true)
    public FlowResponseDTO updateFlow(
            @McpToolParam(description = "Flow ID") Long flowId,
            @McpToolParam(description = "Flow patch data") Map<String, Object> patch) {
        return flowMapper.toResponse(flowService.updateFlow(flowId, patch));
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

    @McpTool(description = "Dry-run a single flow step without persisting request logs", generateOutputSchema = true)
    public DryRunStepResult dryRunStep(
            @McpToolParam(description = "Flow ID") Long flowId,
            @McpToolParam(description = "Dry-run step command") DryRunStepCommand cmd) {
        return flowService.dryRunStep(flowId, cmd);
    }
}
