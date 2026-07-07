package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.services.environments.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EnvironmentMcpTools {

    private final EnvironmentService environmentService;

    @McpTool(description = "Get environment variables for a given environment ID")
    public List<EnvironmentVariableEntity> getEnvironmentVariables(
            @McpToolParam(description = "Environment ID") Long envId) {
        return environmentService.getEnvironmentVariables(envId);
    }

    @McpTool(description = "Update environment variables. Example JSON: { \"removed\": [], \"updated\": [], \"added\": [ { \"key\": \"BASE_URL\", \"value\": \"http://localhost:8080\" } ] }")
    public void updateEnvironmentVariables(
            @McpToolParam(description = "Environment ID") Long envId,
            @McpToolParam(description = "Update command") UpdateEnvironmentVariablesCommand cmd) {
        environmentService.updateEnvironmentVariables(envId, cmd);
    }
}
