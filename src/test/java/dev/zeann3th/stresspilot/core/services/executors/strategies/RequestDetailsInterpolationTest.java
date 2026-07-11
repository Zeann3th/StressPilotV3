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
}
