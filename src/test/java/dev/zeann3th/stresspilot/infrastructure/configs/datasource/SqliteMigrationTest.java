package dev.zeann3th.stresspilot.infrastructure.configs.datasource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteMigrationsApplyToExistingV4Database() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("data.sqlite").toAbsolutePath());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/sqlite/migrations")
                .target("4")
                .load();
        flyway.migrate();

        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/sqlite/migrations")
                .load();
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
    }
}
