package dev.zeann3th.stresspilot.core.services.executors.strategies;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorService;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.HttpExecutionContext;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

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
    private final JsonMapper jsonMapper;

    private OkHttpClient baseClient;

    @PostConstruct
    public void init() {
        log.info("Loading HTTP Executor configurations");
        List<String> keys = List.of(
                ConfigKey.HTTP_CONNECT_TIMEOUT.name(),
                ConfigKey.HTTP_READ_TIMEOUT.name(),
                ConfigKey.HTTP_WRITE_TIMEOUT.name(),
                ConfigKey.HTTP_MAX_POOL_SIZE.name(),
                ConfigKey.HTTP_KEEP_ALIVE_DURATION.name(),
                ConfigKey.HTTP_PROXY_HOST.name(),
                ConfigKey.HTTP_PROXY_PORT.name(),
                ConfigKey.HTTP_PROXY_USERNAME.name(),
                ConfigKey.HTTP_PROXY_PASSWORD.name()
        );

        Map<String, String> configs = configService.getConfigsByKeys(keys);

        int connectTimeout = Integer.parseInt(configs.getOrDefault(ConfigKey.HTTP_CONNECT_TIMEOUT.name(), "10"));
        int readTimeout = Integer.parseInt(configs.getOrDefault(ConfigKey.HTTP_READ_TIMEOUT.name(), "30"));
        int writeTimeout = Integer.parseInt(configs.getOrDefault(ConfigKey.HTTP_WRITE_TIMEOUT.name(), "30"));
        int maxConnections = Integer.parseInt(configs.getOrDefault(ConfigKey.HTTP_MAX_POOL_SIZE.name(), "100"));
        int keepAliveDuration = Integer.parseInt(configs.getOrDefault(ConfigKey.HTTP_KEEP_ALIVE_DURATION.name(), "5"));

        String proxyHost = configs.get(ConfigKey.HTTP_PROXY_HOST.name());
        Integer proxyPort = configs.containsKey(ConfigKey.HTTP_PROXY_PORT.name())
                ? Integer.parseInt(configs.get(ConfigKey.HTTP_PROXY_PORT.name()))
                : null;
        String proxyUser = configs.get(ConfigKey.HTTP_PROXY_USERNAME.name());
        String proxyPass = configs.get(ConfigKey.HTTP_PROXY_PASSWORD.name());

        var clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(maxConnections, keepAliveDuration, TimeUnit.MINUTES))
                .followRedirects(true);

        Proxy proxy;
        if (proxyHost != null && proxyPort != null) {
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
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
    public ExecuteEndpointResponse execute(EndpointEntity endpoint, Map<String, Object> environment, ExecutionContext context) {
        try {
            HttpExecutionContext httpContext = context.getState(HttpExecutionContext.class, HttpExecutionContext::new);

            OkHttpClient client = baseClient.newBuilder()
                    .cookieJar(httpContext)
                    .build();

            Request request = buildRequest(endpoint, environment);

            log.info(request.toString());

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
                        .build();
            }

        } catch (IOException e) {
            log.error("Failed to execute HTTP request for endpoint: {}", endpoint.getName(), e);
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .message("IO Error: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error executing HTTP request", e);
            return ExecuteEndpointResponse.builder()
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

    private RequestBody parseBody(String rawBody, Map<String, String> headers, Map<String, Object> environment) {
        String processedBody = rawBody;
        if (processedBody.contains("{{")) {
            processedBody = DataUtils.replaceVariables(processedBody, environment);
        }
        if (processedBody.contains("@{")) {
            processedBody = MockDataUtils.interpolate(processedBody);
        }
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
