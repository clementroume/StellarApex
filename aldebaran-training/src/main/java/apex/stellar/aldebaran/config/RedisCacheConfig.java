package apex.stellar.aldebaran.config;

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
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis cache configuration with custom TTL per data type.
 *
 * <p><b>Invalidation Strategy:</b>
 *
 * <ul>
 *   <li><b>Movements:</b> Updates evict all entries (details and lists) to ensure consistency.
 *   <li><b>WODs:</b> Individual eviction on update/delete.
 * </ul>
 *
 * <p>Cache Strategy:
 *
 * <ul>
 *   <li>Master Data (Movements, Muscles): 24h TTL
 *   <li>WODs: 1h TTL
 * </ul>
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheConfig.CacheProperties.class)
public class RedisCacheConfig {

  public static final String CACHE_MOVEMENTS = "movements";
  public static final String CACHE_MUSCLES = "muscles";
  public static final String CACHE_WODS = "wods";

  /**
   * Configures a {@link RedisCacheManager} bean with custom TTL (Time-to-Live) values for different
   * cache categories. This method sets up default serialization for keys and values, applies a
   * default TTL, and optionally overrides it for specific cache configurations.
   *
   * @param connectionFactory the {@link RedisConnectionFactory} to be used for creating the cache
   *     manager.
   * @param properties an instance of {@link CacheProperties} containing TTL settings for the
   *     different cache categories.
   * @param objectMapper the Spring-managed ObjectMapper for consistent serialization.
   * @return a {@link RedisCacheManager} instance with the specified configurations.
   */
  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      CacheProperties properties,
      ObjectMapper objectMapper) {

    GenericJacksonJsonRedisSerializer serializer =
        new GenericJacksonJsonRedisSerializer(objectMapper);

    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(properties.defaultTtl()))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    // Apply Key Prefix if configured (Environment Isolation)
    if (properties.keyPrefix() != null && !properties.keyPrefix().isBlank()) {
      defaultConfig =
          defaultConfig.computePrefixWith(
              cacheName -> properties.keyPrefix() + "::" + cacheName + "::");
    }

    RedisCacheConfiguration masterDataConfig =
        defaultConfig.entryTtl(Duration.ofMillis(properties.masterDataTtl()));

    RedisCacheConfiguration wodsConfig =
        defaultConfig.entryTtl(Duration.ofMillis(properties.wodsTtl()));

    Map<String, RedisCacheConfiguration> cacheConfigurations =
        Map.of(
            CACHE_MOVEMENTS, masterDataConfig,
            CACHE_MUSCLES, masterDataConfig,
            CACHE_WODS, wodsConfig);

    return RedisCacheManager.builder(connectionFactory)
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
      @NotNull @Positive Long defaultTtl,
      @NotNull @Positive Long masterDataTtl,
      @NotNull @Positive Long wodsTtl,
      String keyPrefix) {}
}
