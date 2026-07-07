package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.services.environments.EnvironmentService;
import dev.zeann3th.stresspilot.ui.restful.dtos.environment.EnvironmentVariableResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.EnvironmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EnvironmentMcpTools {

    private final EnvironmentService environmentService;
    private final EnvironmentMapper environmentMapper;

    @McpTool(description = "Get environment variables for a given environment ID", generateOutputSchema = true)
    public List<EnvironmentVariableResponseDTO> getEnvironmentVariables(
            @McpToolParam(description = "Environment ID") Long envId) {
        return environmentService.getEnvironmentVariables(envId).stream()
                .map(environmentMapper::toResponse)
                .toList();
    }

    @McpTool(description = "Update environment variables. Example JSON: { \"removed\": [], \"updated\": [], \"added\": [ { \"key\": \"BASE_URL\", \"value\": \"http://localhost:8080\" } ] }")
    public void updateEnvironmentVariables(
            @McpToolParam(description = "Environment ID") Long envId,
            @McpToolParam(description = "Update command") UpdateEnvironmentVariablesCommand cmd) {
        environmentService.updateEnvironmentVariables(envId, cmd);
    }
}
