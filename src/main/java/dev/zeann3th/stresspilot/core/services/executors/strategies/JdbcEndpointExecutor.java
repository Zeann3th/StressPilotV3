package dev.zeann3th.stresspilot.core.services.executors.strategies;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorService;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j(topic = "JdbcEndpointExecutor")
@RequiredArgsConstructor
public class JdbcEndpointExecutor implements EndpointExecutorService {

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return EndpointType.JDBC.name();
    }

    @Override
    public ExecuteEndpointResponse execute(EndpointEntity endpoint, Map<String, Object> environment, ExecutionContext context) {
        long startTime = System.currentTimeMillis();

        String url = parseString(endpoint.getUrl(), environment);
        String user = (String) environment.get("db_user");
        String password = (String) environment.get("db_password");

        String query = parseString(endpoint.getBody(), environment);

        try {
            HikariDataSource dataSource = getDataSource(url, user, password);

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {

                boolean isResultSet = statement.execute(query);
                Object resultData;

                if (isResultSet) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        resultData = processResultSetSafely(resultSet);
                    }
                } else {
                    resultData = Map.of("rowsAffected", statement.getUpdateCount());
                }

                return ExecuteEndpointResponse.builder()
                        .statusCode(200)
                        .success(true)
                        .message("Query executed successfully")
                        .responseTimeMs(System.currentTimeMillis() - startTime)
                        .data(resultData)
                        .rawResponse(DataUtils.parseObjToString(resultData))
                        .build();
            }

        } catch (SQLException e) {
            log.error("SQL Error executing JDBC request for endpoint: {}", endpoint.getName(), e);
            return ExecuteEndpointResponse.builder()
                    .statusCode(500)
                    .success(false)
                    .message("SQL Error: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .data(Map.of("errorCode", e.getErrorCode(), "sqlState", e.getSQLState()))
                    .rawResponse(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error executing JDBC request", e);
            return ExecuteEndpointResponse.builder()
                    .statusCode(500)
                    .success(false)
                    .message("Error: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private String parseString(String input, Map<String, Object> environment) {
        if (input == null) return null;
        String result = input;
        if (result.contains("{{")) {
            result = DataUtils.replaceVariables(result, environment);
        }
        if (result.contains("@{")) {
            result = MockDataUtils.interpolate(result);
        }
        return result;
    }

    private HikariDataSource getDataSource(String url, String user, String password) {
        String cacheKey = url + "||" + (user != null ? user : "") + "||" + (password != null ? password : "");

        return dataSources.computeIfAbsent(cacheKey, _ -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);

            if (user != null) config.setUsername(user);
            if (password != null) config.setPassword(password);

            config.setMaximumPoolSize(20);
            config.setMinimumIdle(2);
            config.setIdleTimeout(60000);
            config.setConnectionTimeout(30000);
            config.setPoolName("StressPilot-Pool-" + Math.abs(cacheKey.hashCode()));

            return new HikariDataSource(config);
        });
    }

    private Map<String, Object> processResultSetSafely(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> previewRows = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        int totalRows = 0;
        int maxPreview = 50;

        while (resultSet.next()) {
            totalRows++;
            if (totalRows <= maxPreview) {
                Map<String, Object> row = HashMap.newHashMap(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), resultSet.getObject(i));
                }
                previewRows.add(row);
            } else {
                break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalRowsFetched", totalRows);
        result.put("preview", previewRows);
        result.put("truncated", totalRows > maxPreview);
        return result;
    }

    @PreDestroy
    public void cleanup() {
        log.info("Closing all JDBC DataSources...");
        dataSources.values().forEach(HikariDataSource::close);
        dataSources.clear();
    }
}
