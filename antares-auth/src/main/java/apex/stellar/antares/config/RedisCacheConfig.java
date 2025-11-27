package apex.stellar.antares.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/** Configuration class for Redis Caching and Repositories. */
@Configuration
@EnableCaching
@EnableRedisRepositories(basePackages = "apex.stellar.antares.repository")
public class RedisCacheConfig {

  @Value("${application.cache.default.ttl}")
  private Long defaultTtl;

  @Value("${application.cache.users.ttl}")
  private Long usersTtl;

  /**
   * Creates and configures a {@link RedisCacheManager} bean for managing cache operations.
   *
   * <p>The default cache configuration includes: - Default entry time-to-live (TTL) of 10 minutes.
   * - Caching of null values is disabled.
   *
   * <p>Additionally, specific cache configurations can be defined, e.g., a custom TTL of 5 minutes
   * for the "users" cache.
   *
   * @param redisConnectionFactory The {@link RedisConnectionFactory} used to connect to the Redis
   *     instance.
   * @return A fully configured {@link RedisCacheManager} instance.
   */
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(defaultTtl))
            .disableCachingNullValues();

    RedisCacheConfiguration usersConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(usersTtl))
            .disableCachingNullValues();

    Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of("users", usersConfig);

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }
}
