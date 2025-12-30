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
 * <p>Cache Strategy:
 *
 * <ul>
 *   <li>Master Data (Movements, Muscles): 24h TTL
 *   <li>WODs: 1h TTL
 *   <li>Scores: 5min TTL
 * </ul>
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheConfig.CacheProperties.class)
public class RedisCacheConfig {

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
    cacheConfigurations.put("movements", masterDataConfig);
    cacheConfigurations.put("movements-by-category", masterDataConfig);
    cacheConfigurations.put("muscles", masterDataConfig);

    cacheConfigurations.put(
        "wods", defaultConfig.entryTtl(Duration.ofMillis(properties.wodsTtl())));

    cacheConfigurations.put(
        "wod-scores", defaultConfig.entryTtl(Duration.ofMillis(properties.scoresTtl())));

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
      @NotNull @Positive Long scoresTtl) {}
}
