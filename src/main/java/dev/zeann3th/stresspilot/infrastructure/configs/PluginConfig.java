package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class PluginConfig {

    @Bean
    public SpringPluginManager pluginManager() {
        String pilotHome = System.getProperty(Constants.PILOT_HOME);
        if (pilotHome == null || pilotHome.isBlank()) {
            pilotHome = System.getenv(Constants.PILOT_HOME);
        }

        Path pluginPath;
        if (pilotHome != null && !pilotHome.isBlank()) {
            pluginPath = Paths.get(pilotHome, "core", "plugins");
        } else {
            String userHome = System.getProperty(Constants.USER_HOME);
            pluginPath = Paths.get(userHome, Constants.APP_DIR, "core", "plugins");
        }

        try {
            Files.createDirectories(pluginPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create plugins directory: " + pluginPath, e);
        }

        return new SpringPluginManager(pluginPath) {
            @Override
            public void init() {
                // no-op
            }
        };
    }
}
