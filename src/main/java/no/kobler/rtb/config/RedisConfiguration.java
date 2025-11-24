package no.kobler.rtb.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;


/**
 * Start an embedded Redis server when property smoothing.type=redis is set.
 * Provide a LettuceConnectionFactory and StringRedisTemplate for Spring Data Redis.
 * <p>
 * Note: embedded-redis is used for convenience in dev/tests. In production point to a managed Redis.
 */
@Configuration
@ConditionalOnProperty(name = "smoothing.type", havingValue = "redis")
public class RedisConfiguration {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")  // Read from config with default 6379
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Connect to the embedded Redis instance we started
        return new LettuceConnectionFactory(redisHost, redisPort);
    }

    /**
     * Expose a StringRedisTemplate backed by the application's RedisConnectionFactory.
     * Spring Boot's auto-configuration will create a RedisConnectionFactory when
     * spring-boot-starter-data-redis is on the classpath and properties are present.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
