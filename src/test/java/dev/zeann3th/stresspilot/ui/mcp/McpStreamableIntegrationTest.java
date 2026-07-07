package dev.zeann3th.stresspilot.ui.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "spring.ai.mcp.server.enabled=true",
        "spring.ai.mcp.server.stdio=false",
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "spring.ai.mcp.server.annotation-scanner.enabled=true",
        "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp"
})
@ActiveProfiles({"test", "dev"})
class McpStreamableIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void streamableHttpClientCanListToolsAndCallProjectTool() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();

            List<String> toolNames = client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();

            assertThat(toolNames)
                    .contains("listProjects", "listFlows", "getRunAnalysisDump");

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("listProjects", Map.of("name", "")));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content()).isNotEmpty();
        }
    }
}
