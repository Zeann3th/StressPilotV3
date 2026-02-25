package dev.zeann3th.stresspilot.core.services.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EndpointEntityMapper {
    private final ObjectMapper objectMapper;

    public String mapHeaders(Map<String, Object> headers) {
        return toJson(headers);
    }

    public Map<String, Object> mapHeaders(String headersJson) {
        return fromJson(headersJson);
    }

    public String mapParameters(Map<String, Object> parameters) {
        return toJson(parameters);
    }

    public Map<String, Object> mapParameters(String parametersJson) {
        return fromJson(parametersJson);
    }

    public String mapBody(Object body) {
        if (body instanceof String str) return str;
        return toJson(body);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
