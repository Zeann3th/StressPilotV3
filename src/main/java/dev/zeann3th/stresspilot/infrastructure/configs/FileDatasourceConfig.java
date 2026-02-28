package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

@Slf4j(topic = "[Database Migrator]")
@Configuration
@ConditionalOnProperty(name = "application.mode", havingValue = "desktop")
@SuppressWarnings("unused")
public class FileDatasourceConfig {

    @Bean
    public DataSource dataSource() {
        try {
            String appHome = System.getenv("PILOT_HOME");
            if (appHome == null || appHome.isEmpty()) {
                log.warn("PILOT_HOME not set, defaulting to user home directory");
                appHome = System.getProperty("user.home") + "/" + Constants.APP_DIR;
            }

            Path dataDir = Paths.get(appHome, "core", "data");
            Files.createDirectories(dataDir);

            Path dbPath = dataDir.resolve(Constants.DB_FILE_NAME);
            boolean dbExists = Files.exists(dbPath);

            String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath() + "?journal_mode=WAL&busy_timeout=5000&foreign_keys=on";
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.sqlite.JDBC");
            dataSource.setUrl(jdbcUrl);

            if (!dbExists) {
                log.info("Database file not found, creating and initializing new database at {}", dbPath);
                try (Connection conn = dataSource.getConnection()) {

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("PRAGMA journal_mode = WAL;");
                    }

                    ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/sqlite/migrations/V1__init.sql"));

                    log.info("Database initialized successfully using V1__init.sql");
                }
            } else {
                log.info("Database file exists at {}, skipping initialization", dbPath);
            }

            log.info("Data source configured with URL: {}", jdbcUrl);
            return dataSource;

        } catch (Exception e) {
            log.error("Failed to configure file datasource", e);
            throw CommandExceptionBuilder.exception(
                    ErrorCode.ER0015,
                    Map.of(Constants.REASON, e.getMessage())
            );
        }
    }

    @Bean
    public HibernatePropertiesCustomizer sqliteHibernateCustomizer() {
        return hibernateProperties ->
            hibernateProperties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
    }
}