package dev.zeann3th.stresspilot.core.services.parsers.strategies;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.parsers.ParserService;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S3776")
public class OpenApiParser implements ParserService {

    private final JsonMapper jsonMapper;

    @Override
    public boolean supports(String filename, String contentType, String content) {
        boolean isJson = (filename != null && filename.endsWith(".json")) ||
                "application/json".equalsIgnoreCase(contentType);

        if (!isJson) {
            return false;
        }

        try {
            JsonNode root = jsonMapper.readTree(content);
            return root.has("openapi") || root.has("swagger");
        } catch (Exception _) {
            return false;
        }
    }

    @Override
    public List<EndpointEntity> parse(String spec) {
        List<EndpointEntity> endpoints = new ArrayList<>();
        try {
            JsonNode root = jsonMapper.readTree(spec);
            JsonNode paths = root.path("paths");

            if (!paths.isMissingNode() && paths.isObject()) {
                for (Map.Entry<String, JsonNode> pathEntry : paths.properties()) {
                    String pathUrl = pathEntry.getKey();
                    JsonNode pathItem = pathEntry.getValue();

                    if (pathItem.isObject()) {
                        for (Map.Entry<String, JsonNode> methodEntry : pathItem.properties()) {
                            String httpMethod = methodEntry.getKey().toUpperCase();
                            JsonNode operation = methodEntry.getValue();

                            if (List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")
                                    .contains(httpMethod)) {
                                endpoints.add(createEndpoint(pathUrl, httpMethod, operation));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Error parsing OpenAPI specification: " + e.getMessage()));
        }
        return endpoints;
    }

    private EndpointEntity createEndpoint(String pathUrl, String httpMethod, JsonNode operation) {
        String name = operation.path("operationId").asString(httpMethod + " " + pathUrl);

        String description = operation.path("description")
                .asString(operation.path("summary").asString(null));

        Map<String, Object> headers = new HashMap<>();
        Map<String, Object> parameters = new HashMap<>();

        JsonNode parametersNode = operation.path("parameters");
        if (parametersNode.isArray()) {
            for (JsonNode param : parametersNode) {
                String in = param.path("in").asString(null);
                String paramName = param.path("name").asString(null);

                if (paramName != null) {
                    if ("query".equals(in)) {
                        parameters.put(paramName, "");
                    } else if ("header".equals(in)) {
                        headers.put(paramName, "");
                    }
                }
            }
        }

        String bodyJson = null;
        JsonNode requestBody = operation.path("requestBody");
        if (!requestBody.isMissingNode()) {
            JsonNode content = requestBody.path("content");
            if (!content.isMissingNode() && content.isObject()) {
                JsonNode appJson = content.path("application/json");
                if (!appJson.isMissingNode()) {
                    bodyJson = "{}";
                }
            }
        }

        return EndpointEntity.builder()
                .name(name)
                .description(description)
                .type("HTTP")
                .httpMethod(httpMethod)
                .url(pathUrl)
                .httpHeaders(headers.isEmpty() ? null : DataUtils.parseObjToJson(headers))
                .httpParameters(parameters.isEmpty() ? null : DataUtils.parseObjToJson(parameters))
                .body(bodyJson)
                .build();
    }
}
