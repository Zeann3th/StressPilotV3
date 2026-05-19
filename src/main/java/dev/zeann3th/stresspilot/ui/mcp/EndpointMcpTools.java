package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.CreateEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteAdhocEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.endpoints.EndpointService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EndpointMcpTools {

    private final EndpointService endpointService;

    @Tool(description = "List endpoints with optional project and name filter")
    public Page<EndpointEntity> listEndpoints(
            @ToolParam(description = "Optional project ID filter") Long projectId,
            @ToolParam(description = "Optional endpoint name filter") String name) {
        return endpointService.getAllEndpoints(projectId, name, PageRequest.of(0, 100));
    }

    @Tool(description = "Get detailed information about an endpoint")
    public EndpointEntity getEndpoint(
            @ToolParam(description = "Endpoint ID") Long id) {
        return endpointService.getEndpointById(id);
    }

    @Tool(description = "Create a new endpoint. Example JSON for CreateEndpointCommand: " +
            "{ \"projectId\": 1, \"name\": \"Get User\", \"type\": \"HTTP\", \"url\": \"http://api/users\", " +
            "\"httpMethod\": \"GET\", \"httpHeaders\": { \"Accept\": \"application/json\" } }")
    public EndpointEntity createEndpoint(
            @ToolParam(description = "Creation command") CreateEndpointCommand cmd) {
        return endpointService.createEndpoint(cmd);
    }

    @Tool(description = "Delete an endpoint")
    public void deleteEndpoint(
            @ToolParam(description = "Endpoint ID") Long id) {
        endpointService.deleteEndpoint(id);
    }

    @Tool(description = "Execute a specific endpoint. Example JSON for ExecuteEndpointCommand: " +
            "{ \"url\": \"http://custom-url\", \"variables\": { \"id\": 123 }, \"httpMethod\": \"POST\" }")
    public ExecuteEndpointResponse executeEndpoint(
            @ToolParam(description = "Endpoint ID") Long endpointId,
            @ToolParam(description = "Execution parameters") ExecuteEndpointCommand cmd) {
        return endpointService.runEndpoint(endpointId, cmd);
    }

    @Tool(description = "Execute an ad-hoc endpoint (not saved). Example JSON for ExecuteAdhocEndpointCommand: " +
            "{ \"url\": \"http://temp-url\", \"type\": \"HTTP\", \"httpMethod\": \"GET\" }")
    public ExecuteEndpointResponse executeAdhocEndpoint(
            @ToolParam(description = "Project ID") Long projectId,
            @ToolParam(description = "Execution parameters") ExecuteAdhocEndpointCommand cmd) {
        return endpointService.runAdhocEndpoint(projectId, cmd);
    }
}
