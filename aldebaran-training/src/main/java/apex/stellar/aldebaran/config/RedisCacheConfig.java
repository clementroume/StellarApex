package apex.stellar.aldebaran.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.HashMap;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis cache configuration with custom TTL per data type.
 *
 * <p><b>Invalidation Strategy:</b>
 * <ul>
 *   <li><b>Movements:</b> Updates evict all entries (details and lists) to ensure consistency.</li>
 *   <li><b>WODs:</b> Individual eviction on update/delete.</li>
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
   * @return a {@link RedisCacheManager} instance with the specified configurations.
   */
  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory, CacheProperties properties) {

    // ObjectMapper
    JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(mapper);

    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(properties.defaultTtl()))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

    RedisCacheConfiguration masterDataConfig =
        defaultConfig.entryTtl(Duration.ofMillis(properties.masterDataTtl()));
    cacheConfigurations.put(CACHE_MOVEMENTS, masterDataConfig);
    cacheConfigurations.put(CACHE_MUSCLES, masterDataConfig);

    cacheConfigurations.put(
        CACHE_WODS, defaultConfig.entryTtl(Duration.ofMillis(properties.wodsTtl())));

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
      @NotNull @Positive Long wodsTtl) {}
}
