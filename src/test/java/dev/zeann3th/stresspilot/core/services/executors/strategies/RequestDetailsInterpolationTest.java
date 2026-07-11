package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestDetailsInterpolationTest {
    @Test
    void testHttpExecutorPopulatesRequestDetails() {
        ConfigService configService = mock(ConfigService.class);
        JsonMapper jsonMapper = new JsonMapper();
        OkHttpClient baseClient = new OkHttpClient();

        HttpEndpointExecutor executor = new HttpEndpointExecutor(configService, jsonMapper, baseClient);

        EndpointEntity endpoint = EndpointEntity.builder()
                .type(EndpointType.HTTP.name())
                .url("http://localhost:7554/v1/users/:userId")
                .httpMethod("POST")
                .httpHeaders("{\"Content-Type\": \"application/json\", \"X-Request-ID\": \"@{uuid}@\"}")
                .body("{\"email\": \"seeduser{{userNumber}}@example.com\"}")
                .build();

        Map<String, Object> environment = new HashMap<>();
        environment.put("userId", "12345");
        environment.put("userNumber", "099");

        BaseExecutionContext context = new BaseExecutionContext();

        ExecuteEndpointResponse response = executor.execute(endpoint, environment, context);

        // Verify request details are populated
        Map<String, Object> details = response.getRequestDetails();
        assertThat(details).isNotNull();
        assertThat(details.get("url")).isEqualTo("http://localhost:7554/v1/users/12345");
        assertThat(details.get("method")).isEqualTo("POST");
        assertThat(details.get("body")).isEqualTo("{\"email\": \"seeduser099@example.com\"}");
        
        String headersJson = (String) details.get("headers");
        assertThat(headersJson).contains("X-Request-ID");
        assertThat(headersJson).doesNotContain("@{uuid}@");
    }

    @Test
    void testGrpcExecutorPopulatesRequestDetails() {
        ConfigService configService = mock(ConfigService.class);
        org.mockito.Mockito.when(configService.getConfigsByKeys(org.mockito.Mockito.anyList())).thenReturn(Map.of());
        JsonMapper jsonMapper = new JsonMapper();

        GrpcEndpointExecutor executor = new GrpcEndpointExecutor(jsonMapper, configService);
        executor.init();

        EndpointEntity endpoint = EndpointEntity.builder()
                .id(1L)
                .name("test-grpc")
                .type(EndpointType.GRPC.name())
                .url("localhost:{{port}}")
                .body("{\"message\": \"hello {{name}}\"}")
                .grpcServiceName("MyService")
                .grpcMethodName("MyMethod")
                .grpcStubPath("/non-existent-path")
                .build();

        Map<String, Object> environment = new HashMap<>();
        environment.put("port", "9090");
        environment.put("name", "world");

        ExecuteEndpointResponse response = executor.execute(endpoint, environment, new BaseExecutionContext());
        Map<String, Object> details = response.getRequestDetails();
        assertThat(details).isNotNull();
        assertThat(details.get("target")).isEqualTo("localhost:9090");
        assertThat(details.get("body")).isEqualTo("{\"message\": \"hello world\"}");
        assertThat(details.get("service")).isEqualTo("MyService");
        assertThat(details.get("method")).isEqualTo("MyMethod");
    }

    @Test
    void testJdbcExecutorPopulatesRequestDetails() {
        JdbcEndpointExecutor executor = new JdbcEndpointExecutor();

        EndpointEntity endpoint = EndpointEntity.builder()
                .id(2L)
                .name("test-jdbc")
                .type(EndpointType.JDBC.name())
                .url("jdbc:mysql://localhost:3306/{{db_name}}")
                .body("SELECT * FROM users WHERE id = {{userId}}")
                .build();

        Map<String, Object> environment = new HashMap<>();
        environment.put("db_name", "testdb");
        environment.put("userId", "456");

        ExecuteEndpointResponse response = executor.execute(endpoint, environment, new BaseExecutionContext());
        Map<String, Object> details = response.getRequestDetails();
        assertThat(details).isNotNull();
        assertThat(details.get("url")).isEqualTo("jdbc:mysql://localhost:3306/testdb");
        assertThat(details.get("query")).isEqualTo("SELECT * FROM users WHERE id = 456");
    }

    @Test
    void testJsExecutorPopulatesRequestDetails() {
        dev.zeann3th.stresspilot.core.services.functions.FunctionService functionService = mock(dev.zeann3th.stresspilot.core.services.functions.FunctionService.class);
        org.mockito.Mockito.when(functionService.getAllFunctions()).thenReturn(java.util.List.of());

        JsEndpointExecutor executor = new JsEndpointExecutor(functionService);
        executor.init();

        EndpointEntity endpoint = EndpointEntity.builder()
                .id(3L)
                .name("test-js")
                .type(EndpointType.JS.name())
                .url("http://localhost/{{endpoint_path}}")
                .body("const x = {{value}}; x + 1;")
                .build();

        Map<String, Object> environment = new HashMap<>();
        environment.put("endpoint_path", "test-path");
        environment.put("value", "41");

        ExecuteEndpointResponse response = executor.execute(endpoint, environment, new BaseExecutionContext());
        Map<String, Object> details = response.getRequestDetails();
        assertThat(details).isNotNull();
        assertThat(details.get("url")).isEqualTo("http://localhost/test-path");
        assertThat(details.get("body")).isEqualTo("const x = 41; x + 1;");
        assertThat(response.getData()).isEqualTo(42);
    }
}
