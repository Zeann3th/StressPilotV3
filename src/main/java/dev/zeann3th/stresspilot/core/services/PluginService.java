package dev.zeann3th.stresspilot.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PluginService implements ApplicationListener<ApplicationReadyEvent> {
    private final SpringPluginManager pluginManager;
    @Override
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        pluginManager.startPlugins();
        log.info("Plugins loaded: {}", pluginManager.getPlugins());
    }
}
