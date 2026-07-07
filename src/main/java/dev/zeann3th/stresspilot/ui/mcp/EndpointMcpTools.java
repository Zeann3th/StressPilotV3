package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.CreateEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteAdhocEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.endpoints.EndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EndpointMcpTools {

    private final EndpointService endpointService;

    @McpTool(description = "List endpoints with optional project and name filter")
    public Page<EndpointEntity> listEndpoints(
            @McpToolParam(description = "Optional project ID filter") Long projectId,
            @McpToolParam(description = "Optional endpoint name filter") String name) {
        return endpointService.getAllEndpoints(projectId, name, PageRequest.of(0, 100));
    }

    @McpTool(description = "Get detailed information about an endpoint")
    public EndpointEntity getEndpoint(
            @McpToolParam(description = "Endpoint ID") Long id) {
        return endpointService.getEndpointById(id);
    }

    @McpTool(description = "Create a new endpoint. Example JSON for CreateEndpointCommand: " +
            "{ \"projectId\": 1, \"name\": \"Get User\", \"type\": \"HTTP\", \"url\": \"http://api/users\", " +
            "\"httpMethod\": \"GET\", \"httpHeaders\": { \"Accept\": \"application/json\" } }")
    public EndpointEntity createEndpoint(
            @McpToolParam(description = "Creation command") CreateEndpointCommand cmd) {
        return endpointService.createEndpoint(cmd);
    }

    @McpTool(description = "Delete an endpoint")
    public void deleteEndpoint(
            @McpToolParam(description = "Endpoint ID") Long id) {
        endpointService.deleteEndpoint(id);
    }

    @McpTool(description = "Execute a specific endpoint. Example JSON for ExecuteEndpointCommand: " +
            "{ \"url\": \"http://custom-url\", \"variables\": { \"id\": 123 }, \"httpMethod\": \"POST\" }")
    public ExecuteEndpointResponse executeEndpoint(
            @McpToolParam(description = "Endpoint ID") Long endpointId,
            @McpToolParam(description = "Execution parameters") ExecuteEndpointCommand cmd) {
        return endpointService.runEndpoint(endpointId, cmd);
    }

    @McpTool(description = "Execute an ad-hoc endpoint (not saved). Example JSON for ExecuteAdhocEndpointCommand: " +
            "{ \"url\": \"http://temp-url\", \"type\": \"HTTP\", \"httpMethod\": \"GET\" }")
    public ExecuteEndpointResponse executeAdhocEndpoint(
            @McpToolParam(description = "Project ID") Long projectId,
            @McpToolParam(description = "Execution parameters") ExecuteAdhocEndpointCommand cmd) {
        return endpointService.runAdhocEndpoint(projectId, cmd);
    }
}
