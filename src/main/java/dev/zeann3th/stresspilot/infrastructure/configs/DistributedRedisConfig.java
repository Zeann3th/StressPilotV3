package dev.zeann3th.stresspilot.infrastructure.configs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(DataRedisProperties.class)
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedRedisConfig {
    @Bean
    LettuceConnectionFactory redisConnectionFactory(DataRedisProperties properties) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
        configuration.setDatabase(properties.getDatabase());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            configuration.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            configuration.setPassword(RedisPassword.of(properties.getPassword()));
        }
        return new LettuceConnectionFactory(configuration);
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    RedisStartupValidator redisStartupValidator(RedisConnectionFactory redisConnectionFactory) {
        return new RedisStartupValidator(redisConnectionFactory);
    }

    static final class RedisStartupValidator {
        RedisStartupValidator(RedisConnectionFactory redisConnectionFactory) {
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                connection.ping();
            }
        }
    }
}
