package dev.zeann3th.stresspilot.core.services.parsers.impl;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ParserType;
import dev.zeann3th.stresspilot.core.services.parsers.ParserService;
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
public class PostmanParser implements ParserService {

    private final JsonMapper jsonMapper;

    @Override
    public String getType() {
        return ParserType.POSTMAN.name();
    }

    @Override
    public List<EndpointEntity> parse(String spec) {
        List<EndpointEntity> endpoints = new ArrayList<>();
        try {
            JsonNode root = jsonMapper.readTree(spec);
            JsonNode items = root.path("item");
            extractEndpoints(items, endpoints);
        } catch (Exception e) {
            throw (RuntimeException) e;
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

    @SuppressWarnings("unchecked")
    private EndpointEntity createEndpoint(JsonNode item) {
        JsonNode request = item.path("request");

        // Headers
        Map<String, Object> headers = new HashMap<>();
        for (JsonNode header : request.path("header")) {
            headers.put(header.path("key").asString(), header.path("value").asString());
        }

        // Query parameters
        Map<String, Object> parameters = new HashMap<>();
        for (JsonNode param : request.path("url").path("query")) {
            parameters.put(param.path("key").asString(), param.path("value").asString());
        }

        // Body Processing
        Object bodyObj = null;
        if ("raw".equals(request.path("body").path("mode").asString())) {
            String rawBody = request.path("body").path("raw").asString();
            if (!rawBody.isEmpty()) {
                try {
                    Object parsed = jsonMapper.readValue(rawBody, Object.class);
                    switch (parsed) {
                        case Map<?, ?> map -> bodyObj = convertValuesToTemplate((Map<String, Object>) map);
                        case List<?> list -> bodyObj = convertListToTemplate(new ArrayList<>(list), "item");
                        default -> bodyObj = parsed;
                    }
                } catch (Exception _) {
                    bodyObj = rawBody;
                }
            }
        }

        String bodyJson = null;
        if (bodyObj != null) {
            if (bodyObj instanceof String str) {
                bodyJson = str.isBlank() ? "{}" : str;
            } else {
                bodyJson = jsonMapper.writeValueAsString(bodyObj);
            }
        }

        return EndpointEntity.builder()
                .name(item.path("name").asString())
                .description(item.path("description").asString(null))
                .type("HTTP")
                .httpMethod(request.path("method").asString())
                .url(request.path("url").path("raw").asString())
                .httpHeaders(headers.isEmpty() ? null : jsonMapper.writeValueAsString(headers))
                .httpParameters(parameters.isEmpty() ? null : jsonMapper.writeValueAsString(parameters))
                .body(bodyJson)
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