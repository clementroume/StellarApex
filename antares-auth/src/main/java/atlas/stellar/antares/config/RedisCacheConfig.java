package atlas.stellar.antares.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Configuration class for setting up Redis caching.
 *
 * <p>Enables Spring's caching abstraction and configures the {@link RedisCacheManager} with default
 * and cache-specific Time-To-Live (TTL) settings.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

  /**
   * Configures the Spring-native CacheManager to use Redis.
   *
   * <p>This sets a default TTL of 10 minutes for all caches and a specific 7-day TTL for the
   * 'refreshTokens' cache.
   *
   * @param redisConnectionFactory The autoconfigured factory from Spring Boot.
   * @return A RedisCacheManager instance.
   */
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues();

    return RedisCacheManager.builder(redisConnectionFactory).cacheDefaults(config).build();
  }
}
