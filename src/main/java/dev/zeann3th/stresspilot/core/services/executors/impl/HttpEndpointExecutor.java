package dev.zeann3th.stresspilot.core.services.executors.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorService;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.EndpointResponse;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Slf4j(topic = "HttpEndpointExecutor")
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class HttpEndpointExecutor implements EndpointExecutorService {

    private static final Pattern PATH_VAR_PATTERN = Pattern.compile("(?<=/):(\\w+)");

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    private OkHttpClient baseClient;

    @PostConstruct
    public void init() {
        log.info("Loading HTTP Executor configurations");
        int connectTimeout = configService.getValue(ConfigKey.HTTP_CONNECT_TIMEOUT.name()).map(Integer::parseInt).orElse(10);
        int readTimeout = configService.getValue(ConfigKey.HTTP_READ_TIMEOUT.name()).map(Integer::parseInt).orElse(30);
        int writeTimeout = configService.getValue(ConfigKey.HTTP_WRITE_TIMEOUT.name()).map(Integer::parseInt).orElse(30);
        int maxConnections = configService.getValue(ConfigKey.HTTP_MAX_POOL_SIZE.name()).map(Integer::parseInt).orElse(100);
        int keepAliveDuration = configService.getValue(ConfigKey.HTTP_KEEP_ALIVE_DURATION.name()).map(Integer::parseInt).orElse(5);

        String proxyHost = configService.getValue(ConfigKey.HTTP_PROXY_HOST.name()).orElse(null);
        Integer proxyPort = configService.getValue(ConfigKey.HTTP_PROXY_PORT.name()).map(Integer::parseInt).orElse(null);
        String proxyUser = configService.getValue(ConfigKey.HTTP_PROXY_USERNAME.name()).orElse(null);
        String proxyPass = configService.getValue(ConfigKey.HTTP_PROXY_PASSWORD.name()).orElse(null);

        
        var clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(maxConnections, keepAliveDuration, TimeUnit.MINUTES))
                .followRedirects(true);

        if (proxyHost != null && proxyPort != null) {
          Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
          clientBuilder.proxy(proxy);

          if (proxyUser != null && proxyPass != null) {
            clientBuilder.proxyAuthenticator((route, response) -> {
              String credential = Credentials.basic(proxyUser, proxyPass);
              return response.request().newBuilder()
                .header("Proxy-Authorization", credential)
                .build();
            });
          }
        }

        baseClient = clientBuilder.build();

        log.info("HTTP Executor initialized with timeout: connect={}s, read={}s, write={}s",
                connectTimeout, readTimeout, writeTimeout);
    }

    @Override
    public String getType() {
        return EndpointType.HTTP.name();
    }

    @Override
    public EndpointResponse execute(EndpointEntity endpointEntity,
                                       Map<String, Object> environment,
                                       CookieJar cookieJar) {
        try {
            OkHttpClient client = cookieJar != null
                    ? baseClient.newBuilder().cookieJar(cookieJar).build()
                    : baseClient;

            Request request = buildRequest(endpointEntity, environment);

            long startTime = System.currentTimeMillis();
            try (Response response = client.newCall(request).execute()) {
                long responseTimeMs = System.currentTimeMillis() - startTime;

                String rawResponse = response.body() != null ? response.body().string() : "";

                return EndpointResponse.builder()
                        .statusCode(response.code())
                        .success(response.isSuccessful())
                        .message(response.message())
                        .responseTimeMs(responseTimeMs)
                        .data(parseResponseData(rawResponse))
                        .rawResponse(rawResponse)
                        .build();
            }

        } catch (IOException e) {
            log.error("Failed to execute HTTP request for endpoint: {}", endpointEntity.getName(), e);
            return EndpointResponse.builder()
                    .success(false)
                    .message("IO Error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error executing HTTP request", e);
            return EndpointResponse.builder()
                    .success(false)
                    .message("Unexpected error: " + e.getMessage())
                    .build();
        }
    }

    private Request buildRequest(EndpointEntity endpoint, Map<String, Object> environment) {
        String url = parseUrl(endpoint.getUrl(), environment);

        Map<String, String> headers = parseHeaders(endpoint.getHttpHeaders(), environment);

        Request.Builder builder = new Request.Builder().url(url);
        headers.forEach(builder::addHeader);

        RequestBody requestBody = null;
        if (DataUtils.hasText(endpoint.getBody())) {
            requestBody = parseBody(endpoint.getBody(), headers, environment);
        }

        String method = endpoint.getHttpMethod().toUpperCase();
        switch (method) {
            case "GET" -> builder.get();
            case "POST" -> builder.post(requestBody != null ? requestBody : RequestBody.create("", null));
            case "PUT" -> builder.put(requestBody != null ? requestBody : RequestBody.create("", null));
            case "DELETE" -> builder.delete(requestBody);
            case "PATCH" -> builder.patch(requestBody != null ? requestBody : RequestBody.create("", null));
            case "HEAD" -> builder.head();
            case "OPTIONS" -> builder.method("OPTIONS", null);
            default -> builder.method(method, requestBody);
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
            Map<String, String> rawHeaders = objectMapper.readValue(headersJson, new TypeReference<>() {});
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

    private RequestBody parseBody(String rawBody, Map<String, String> headers, Map<String, Object> environment) {
        String processedBody = rawBody;
        if (processedBody.contains("{{")) {
            processedBody = DataUtils.replaceVariables(processedBody, environment);
        }
        if (processedBody.contains("@{")) {
            processedBody = MockDataUtils.interpolate(processedBody);
        }

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
                throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
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
                return objectMapper.readValue(rawResponse, new TypeReference<Map<String, Object>>() {});
            } else if (rawResponse.trim().startsWith("[")) {
                return objectMapper.readValue(rawResponse, new TypeReference<List<Object>>() {});
            } else {
                return rawResponse;
            }
        } catch (Exception e) {
            log.debug("Failed to parse response JSON", e);
            return rawResponse;
        }
    }
}
