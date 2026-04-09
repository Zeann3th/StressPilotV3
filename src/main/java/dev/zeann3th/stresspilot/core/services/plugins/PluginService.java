package dev.zeann3th.stresspilot.core.services.plugins;

import org.pf4j.PluginDescriptor;

import java.util.List;

public interface PluginService {
    void reloadPlugin(String pluginId);

    void reloadAllPlugins();

    List<PluginDescriptor> getAllPluginDescriptors();
}
