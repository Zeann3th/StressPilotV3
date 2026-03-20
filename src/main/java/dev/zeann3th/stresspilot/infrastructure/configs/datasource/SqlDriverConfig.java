package dev.zeann3th.stresspilot.infrastructure.configs.datasource;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.infrastructure.utils.DriverShim;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

@Configuration
@Slf4j(topic = "[SQL Driver Loader]")
public class SqlDriverConfig {

    private String driversPath;

    @PostConstruct
    public void init() {
        String appHome = System.getenv("PILOT_HOME");
        if (appHome == null || appHome.isEmpty()) {
            appHome = System.getProperty("user.home") + "/" + Constants.APP_DIR;
        }

        driversPath = Paths.get(appHome, "core", "drivers").toString();

        refreshDrivers();
    }

    public synchronized void refreshDrivers() {
        File folder = new File(driversPath);

        if (!folder.exists()) {
            log.warn("Drivers directory not found: {}", folder.getAbsolutePath());
            return;
        }

        File[] files = folder.listFiles((_, name) -> name.endsWith(".jar"));
        if (files == null) {
            log.warn("No files found in drivers directory: {}", folder.getAbsolutePath());
            return;
        }

        List<URL> urls = new ArrayList<>();
        for (File file : files) {
            try {
                urls.add(file.toURI().toURL());
                log.info("Found driver jar: {}", file.getName());
            } catch (MalformedURLException e) {
                log.error("Invalid driver jar path: {}", file.getAbsolutePath(), e);
            }
        }

        if (urls.isEmpty()) {
            return;
        }

        URLClassLoader driverClassLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());

        ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class, driverClassLoader);

        int count = 0;
        for (Driver driver : loadedDrivers) {
            try {
                DriverManager.registerDriver(new DriverShim(driver));
                log.info("Registered JDBC Driver: {}", driver.getClass().getName());
                count++;
            } catch (SQLException e) {
                log.error("Failed to register driver: {}", driver.getClass().getName(), e);
            }
        }
        log.info("Loaded {} JDBC drivers from {} jars in {}", count, urls.size(), folder.getAbsolutePath());
    }
}
