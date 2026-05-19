package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ConfigMcpTools {

    private final ConfigService configService;

    @Tool(description = "List all application configurations")
    public Map<String, String> listConfigs() {
        return configService.getAllConfigs();
    }

    @Tool(description = "Update a specific configuration value")
    public void updateConfig(
            @ToolParam(description = "Configuration key") ConfigKey key,
            @ToolParam(description = "New value") String value) {
        configService.setValue(key.name(), value);
    }
}
