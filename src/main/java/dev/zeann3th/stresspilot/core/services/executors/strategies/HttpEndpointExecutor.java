package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutor;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.HttpExecutionContext;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@Slf4j(topic = "HttpEndpointExecutor")
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class HttpEndpointExecutor implements EndpointExecutor {

    private static final Pattern PATH_VAR_PATTERN = Pattern.compile("(?<=/):(\\w+)");

    private final ConfigService configService;
    private final JsonMapper jsonMapper;
    private final OkHttpClient baseClient;

    @Override
    public String getType() {
        return EndpointType.HTTP.name();
    }

    @Override
    public ExecuteEndpointResponse execute(EndpointEntity endpoint, Map<String, Object> environment, ExecutionContext context) {
        Map<String, Object> requestDetails = null;
        try {
            HttpExecutionContext httpContext = context.getState(HttpExecutionContext.class, HttpExecutionContext::new);

            OkHttpClient client = httpContext.getHttpClient();
            if (client == null) {
                client = baseClient.newBuilder()
                        .cookieJar(httpContext)
                        .build();
                httpContext.setHttpClient(client);
            }

            // Perform the interpolation of URL, headers, parameters, and body EXACTLY ONCE at the start
            String url = endpoint.getUrl() != null ? parseUrl(endpoint.getUrl(), environment) : null;
            Map<String, String> headers = parseHeaders(endpoint.getHttpHeaders(), environment);
            String parameters = endpoint.getHttpParameters() != null ? DataUtils.replaceVariables(MockDataUtils.interpolate(endpoint.getHttpParameters()), environment) : null;
            
            String requestBodyStr = null;
            if (DataUtils.hasText(endpoint.getBody())) {
                requestBodyStr = endpoint.getBody();
                if (requestBodyStr.contains("{{")) {
                    requestBodyStr = DataUtils.replaceVariables(requestBodyStr, environment);
                }
                if (requestBodyStr.contains("@{")) {
                    requestBodyStr = MockDataUtils.interpolate(requestBodyStr);
                }
            }

            // Build the OkHttp request using these pre-interpolated variables
            Request request = buildRequest(endpoint, url, headers, requestBodyStr);

            log.debug("HTTP request after interpolation: {}", request);

            // Populate requestDetails using the exact same variables
            requestDetails = new LinkedHashMap<>();
            requestDetails.put("endpointId", endpoint.getId());
            requestDetails.put("endpointName", endpoint.getName());
            requestDetails.put("type", endpoint.getType());
            requestDetails.put("method", request.method());
            requestDetails.put("url", url);
            requestDetails.put("headers", jsonMapper.writeValueAsString(headers));
            requestDetails.put("parameters", parameters);
            requestDetails.put("body", requestBodyStr);

            long startTime = System.currentTimeMillis();
            try (Response response = client.newCall(request).execute()) {
                long responseTimeMs = System.currentTimeMillis() - startTime;

                String rawResponse = response.body() != null ? response.body().string() : "";

                return ExecuteEndpointResponse.builder()
                        .statusCode(response.code())
                        .success(response.isSuccessful())
                        .message(response.message())
                        .responseTimeMs(responseTimeMs)
                        .data(parseResponseData(rawResponse))
                        .rawResponse(rawResponse)
                        .requestDetails(requestDetails)
                        .build();
            }

        } catch (IOException e) {
            log.error("Failed to execute HTTP request for endpoint: {}", endpoint.getName(), e);
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .message("IO Error: " + e.getMessage())
                    .requestDetails(requestDetails)
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error executing HTTP request", e);
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .message("Unexpected error: " + e.getMessage())
                    .requestDetails(requestDetails)
                    .build();
        }
    }

    private Request buildRequest(EndpointEntity endpoint, String url, Map<String, String> headers, String requestBodyStr) {
        Request.Builder builder = new Request.Builder().url(url);
        headers.forEach(builder::addHeader);

        RequestBody requestBody = null;
        if (requestBodyStr != null) {
            requestBody = createRequestBody(requestBodyStr, headers);
        }

        String method = endpoint.getHttpMethod().toUpperCase();
        switch (method) {
            case "GET":
                builder.get();
                break;
            case "POST":
                builder.post(requestBody != null ? requestBody : RequestBody.create("", null));
                break;
            case "PUT":
                builder.put(requestBody != null ? requestBody : RequestBody.create("", null));
                break;
            case "DELETE":
                builder.delete(requestBody);
                break;
            case "PATCH":
                builder.patch(requestBody != null ? requestBody : RequestBody.create("", null));
                break;
            case "HEAD":
                builder.head();
                break;
            case "OPTIONS":
                builder.method("OPTIONS", null);
                break;
            default:
                builder.method(method, requestBody);
        }

        return builder.build();
    }

    private String parseUrl(String url, Map<String, Object> environment) {
        if (url.contains("{{")) {
            url = DataUtils.replaceVariables(url, environment);
        }
        if (url.contains("@{")) {
            url = MockDataUtils.interpolate(url);
        }
        if (url.contains(":")) {
            url = interpolatePathVariablesColon(url, environment);
        }
        return url;
    }

    private Map<String, String> parseHeaders(String headersJson, Map<String, Object> environment) {
        if (headersJson == null || headersJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, String> rawHeaders = jsonMapper.readValue(headersJson, new TypeReference<>() {});
            Map<String, String> processedHeaders = new HashMap<>();

            rawHeaders.forEach((key, value) -> {
                String processedValue = value;
                if (processedValue.contains("{{")) {
                    processedValue = DataUtils.replaceVariables(processedValue, environment);
                }
                if (processedValue.contains("@{")) {
                    processedValue = MockDataUtils.interpolate(processedValue);
                }
                processedHeaders.put(key, processedValue);
            });

            return processedHeaders;
        } catch (Exception e) {
            log.warn("Failed to parse headers: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private RequestBody createRequestBody(String processedBody, Map<String, String> headers) {
        log.debug("Request body after processing: {}", processedBody);

        String contentType = headers.entrySet().stream()
                .filter(e -> "content-type".equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("application/json; charset=utf-8");

        MediaType mediaType;
        try {
            mediaType = MediaType.parse(contentType);
        } catch (Exception _) {
            log.warn("Invalid Content-Type: {}, defaulting to application/json", contentType);
            mediaType = MediaType.parse("application/json; charset=utf-8");
        }
        return RequestBody.create(processedBody, mediaType);
    }

    private String interpolatePathVariablesColon(String url, Map<String, Object> env) {
        if (url == null || !url.contains(":")) return url;

        return PATH_VAR_PATTERN.matcher(url).replaceAll(mr -> {
            String key = mr.group(1);
            Object value = env.get(key);
            if (value == null) {
                throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                        Map.of(Constants.REASON, "Missing path variable: " + key));
            }
            return value.toString();
        });
    }

    private Object parseResponseData(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return Map.of();
        }
        try {
            if (rawResponse.trim().startsWith("{")) {
                return jsonMapper.readValue(
                        rawResponse,
                        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                        }
                );
            } else if (rawResponse.trim().startsWith("[")) {
                return jsonMapper.readValue(
                        rawResponse,
                        new tools.jackson.core.type.TypeReference<List<Object>>() {
                        }
                );
            } else {
                return rawResponse;
            }
        } catch (Exception e) {
            log.debug("Failed to parse response JSON", e);
            return rawResponse;
        }
    }
}
