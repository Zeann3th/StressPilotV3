package dev.zeann3th.stresspilot.core.services.parsers.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.ParserType;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.parsers.EndpointEntityMapper;
import dev.zeann3th.stresspilot.core.services.parsers.ParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PostmanParser implements ParserService {

    private final ObjectMapper objectMapper;
    private final EndpointEntityMapper entityMapper;

    @Override
    public String getType() {
        return ParserType.POSTMAN.name();
    }

    @Override
    public List<EndpointEntity> parse(String spec) {
        List<EndpointEntity> endpoints = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(spec);
            JsonNode items = root.path("item");
            extractEndpoints(items, endpoints);
        } catch (Exception e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.ENDPOINT_PARSE_ERROR);
        }
        return endpoints;
    }

    private void extractEndpoints(JsonNode items, List<EndpointEntity> endpoints) {
        if (items.isArray()) {
            for (JsonNode item : items) {
                if (item.has("item")) {
                    extractEndpoints(item.get("item"), endpoints);
                } else if (item.has("request")) {
                    endpoints.add(createEndpoint(item));
                }
            }
        }
    }

    private EndpointEntity createEndpoint(JsonNode item) {
        JsonNode request = item.path("request");

        // Headers
        Map<String, Object> headers = new HashMap<>();
        for (JsonNode header : request.path("header")) {
            headers.put(header.path("key").asText(), header.path("value").asText());
        }

        // Query parameters
        Map<String, Object> parameters = new HashMap<>();
        for (JsonNode param : request.path("url").path("query")) {
            parameters.put(param.path("key").asText(), param.path("value").asText());
        }

        // Body
        Object body = null;
        if ("raw".equals(request.path("body").path("mode").asText())) {
            String rawBody = request.path("body").path("raw").asText();
            if (!rawBody.isEmpty()) {
                try {
                    Object parsed = objectMapper.readValue(rawBody, Object.class);

                    switch (parsed) {
                        case Map<?, ?> map -> body = convertValuesToTemplate((Map<String, Object>) map);
                        case List<?> list -> body = convertListToTemplate(new ArrayList<>(list), "item");
                        default -> body = parsed;
                    }
                } catch (Exception _) {
                    body = rawBody;
                }
            }
        }

        return EndpointEntity.builder()
                .name(item.path("name").asText())
                .description(item.path("description").asText(null))
                .type("HTTP")
                .httpMethod(request.path("method").asText())
                .url(request.path("url").path("raw").asText())
                .httpHeaders(headers.isEmpty() ? null : entityMapper.mapHeaders(headers))
                .httpParameters(parameters.isEmpty() ? null : entityMapper.mapParameters(parameters))
                .body(entityMapper.mapBody(body))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertValuesToTemplate(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();

            if (value instanceof String || value instanceof Number) {
                result.put(key, "{{" + key + "}}");
            } else if (value instanceof Map) {
                result.put(key, convertValuesToTemplate((Map<String, Object>) value));
            } else if (value instanceof List) {
                result.put(key, convertListToTemplate((List<Object>) value, key));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertListToTemplate(List<Object> input, String parentKey) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Object item = input.get(i);
            String indexedKey = parentKey + "_" + i;

            if (item instanceof String || item instanceof Number) {
                result.add("{{" + indexedKey + "}}");
            } else if (item instanceof Map) {
                result.add(convertValuesToTemplate((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(convertListToTemplate((List<Object>) item, indexedKey));
            } else {
                result.add(item);
            }
        }
        return result;
    }
}
