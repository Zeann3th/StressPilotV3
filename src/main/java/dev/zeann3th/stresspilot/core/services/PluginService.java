package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PluginService implements ApplicationListener<ApplicationReadyEvent> {
    private final SpringPluginManager pluginManager;
    private final ActiveRunRegistry activeRunRegistry;

    @Override
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        pluginManager.loadPlugins();

        List<PluginWrapper> resolvedPlugins = pluginManager.getResolvedPlugins();
        for (PluginWrapper plugin : resolvedPlugins) {
            safeStartPlugin(plugin.getPluginId());
        }

        log.info("Plugins initialization complete. Active plugins: {}", getActivePluginIds());
    }

    public void reloadPlugin(String pluginId) {
        if (activeRunRegistry.hasActiveRuns()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0024,
                    Map.of(Constants.REASON, "Cannot reload plugin '" + pluginId + "' while a run is actively executing."));
        }

        log.info("Initiating reload for plugin: {}", pluginId);

        PluginWrapper pluginWrapper = pluginManager.getPlugin(pluginId);
        if (pluginWrapper == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0024,
                    Map.of(Constants.REASON, "Cannot reload: Plugin '" + pluginId + "' is not currently loaded"));
        }

        Path pluginPath = pluginWrapper.getPluginPath();

        pluginManager.stopPlugin(pluginId);
        pluginManager.unloadPlugin(pluginId);

        String newPluginId = pluginManager.loadPlugin(pluginPath);
        if (newPluginId != null) {
            pluginManager.startPlugin(newPluginId);
            log.info("Plugin '{}' reloaded successfully.", newPluginId);
        } else {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0024,
                    Map.of(Constants.REASON, "Failed to reload plugin from path: " + pluginPath));
        }
    }

    public void reloadAllPlugins() {
        if (activeRunRegistry.hasActiveRuns()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0024,
                    Map.of(Constants.REASON, "Cannot perform bulk plugin reload while a run is actively executing."));
        }

        log.info("Initiating full plugin reload...");

        List<PluginWrapper> existingPlugins = pluginManager.getPlugins();
        for (PluginWrapper plugin : existingPlugins) {
            String id = plugin.getPluginId();
            pluginManager.stopPlugin(id);
            pluginManager.unloadPlugin(id);
        }

        pluginManager.loadPlugins();
        pluginManager.startPlugins();

        List<PluginWrapper> badPlugins = pluginManager.getPlugins().stream()
                .filter(plugin -> plugin.getPluginState() != org.pf4j.PluginState.STARTED)
                .toList();

        if (!badPlugins.isEmpty()) {
            List<String> badIds = badPlugins.stream()
                    .map(PluginWrapper::getPluginId)
                    .toList();

            log.warn("Cleaning up plugins that failed to start: {}", badIds);

            for (String badId : badIds) {
                pluginManager.unloadPlugin(badId);
            }

            throw CommandExceptionBuilder.exception(ErrorCode.ER0024,
                    Map.of(Constants.REASON, "Partial reload complete. Good plugins started, but these failed and were unloaded: " + String.join(", ", badIds)));
        }

        log.info("Full reload complete. Active plugins: {}", getActivePluginIds());
    }

    private boolean safeStartPlugin(String pluginId) {
        try {
            PluginState state = pluginManager.startPlugin(pluginId);
            return state == PluginState.STARTED;
        } catch (Throwable t) {
            log.error("CRITICAL: Bad plugin detected. Failed to start '{}'. Unloading to protect Stress Pilot core.", pluginId, t);
            pluginManager.unloadPlugin(pluginId);
            return false;
        }
    }

    private List<String> getActivePluginIds() {
        return pluginManager.getPlugins().stream()
                .map(PluginWrapper::getPluginId)
                .toList();
    }

    public List<PluginDescriptor> getAllPluginDescriptors() {
        return pluginManager.getPlugins().stream()
                .map(PluginWrapper::getDescriptor)
                .toList();
    }
}
