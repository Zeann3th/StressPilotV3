package dev.zeann3th.stresspilot.infrastructure.configs.datasource;

import com.zaxxer.hikari.HikariDataSource;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "application.message.database", name = "source", havingValue = "secondary")
public class SecondaryDatasourceConfig {

    @Bean(name = "secondaryDataSource")
    public DataSource secondaryDataSource(RequestLogWriterProperties properties) {
        RequestLogWriterProperties.Datasource dbProps = properties.getDatasource();

        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(dbProps.getUrl())
                .username(dbProps.getUsername())
                .password(dbProps.getPassword())
                .driverClassName(dbProps.getDriverClassName())
                .build();
    }

    @Bean(name = "secondaryJdbcTemplate")
    public JdbcTemplate secondaryJdbcTemplate(@Qualifier("secondaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
