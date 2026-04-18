package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j(topic = "HttpClientConfig")
public class HttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient(@Lazy ConfigService configService) {
        log.info("Initializing shared OkHttpClient bean");
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

        if (proxyHost != null && proxyPort != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            clientBuilder.proxy(proxy);

            if (proxyUser != null && proxyPass != null) {
                clientBuilder.proxyAuthenticator((_, response) -> {
                    String credential = Credentials.basic(proxyUser, proxyPass);
                    return response.request().newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build();
                });
            }
        }

        return clientBuilder.build();
    }
}
