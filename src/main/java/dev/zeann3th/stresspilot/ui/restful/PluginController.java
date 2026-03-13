package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.PluginService;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginDescriptor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
@ResponseWrapper
@Tag(name = "Plugins", description = "APIs for managing plugins")
public class PluginController {
    private final PluginService pluginService;

    @PostMapping("/{pluginId}/reload")
    public void reloadPlugin(@PathVariable String pluginId) {
        pluginService.reloadPlugin(pluginId);
    }

    @PostMapping("/reload-all")
    public void reloadAllPlugins() {
        pluginService.reloadAllPlugins();
    }

    @GetMapping("/list")
    public List<PluginDescriptor> listPlugins() {
        return pluginService.getAllPluginDescriptors();
    }
}
