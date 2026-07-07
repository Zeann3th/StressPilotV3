package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConfigMcpTools {

    private final ConfigService configService;

    @McpTool(description = "List all application configurations")
    public Map<String, String> listConfigs() {
        return configService.getAllConfigs();
    }

    @McpTool(description = "Update a specific configuration value")
    public void updateConfig(
            @McpToolParam(description = "Configuration key") ConfigKey key,
            @McpToolParam(description = "New value") String value) {
        configService.setValue(key.name(), value);
    }
}
