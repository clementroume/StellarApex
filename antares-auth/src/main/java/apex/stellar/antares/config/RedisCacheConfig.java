package apex.stellar.antares.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.validation.annotation.Validated;

/** Configuration class for Redis Caching and Repositories. */
@Configuration
@EnableCaching
@EnableRedisRepositories(basePackages = "apex.stellar.antares.repository.redis")
@EnableConfigurationProperties(RedisCacheConfig.CacheProperties.class)
public class RedisCacheConfig {

  /**
   * Configures the Spring-native CacheManager using the inner configuration properties.
   *
   * @param redisConnectionFactory The Redis connection factory.
   * @param properties The injected inner configuration properties.
   */
  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory redisConnectionFactory, CacheProperties properties) {

    // 1. Default Configuration
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(properties.defaultTtl()))
            .disableCachingNullValues();

    // 2. Specific Configuration for 'users'
    RedisCacheConfiguration usersConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(properties.usersTtl()))
            .disableCachingNullValues();

    // 3. Map configurations
    Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of("users", usersConfig);

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }

  /**
   * Inner configuration record for Cache properties. Maps properties starting with
   * 'application.cache'.
   */
  @ConfigurationProperties(prefix = "application.cache")
  @Validated
  public record CacheProperties(
      @NotNull @Positive Long defaultTtl, @NotNull @Positive Long usersTtl) {}
}
