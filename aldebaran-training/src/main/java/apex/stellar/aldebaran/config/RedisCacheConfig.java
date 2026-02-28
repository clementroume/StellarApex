package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.dto.MovementSummaryResponse;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.dto.WodResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * Configuration class for Redis-based caching. Sets up specific serializers and TTLs for different
 * cache regions.
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheConfig.CacheProperties.class)
public class RedisCacheConfig {

  /** Cache name for movement master data. */
  public static final String CACHE_MOVEMENTS = "movements";

  /** Cache name for a specific muscle. */
  public static final String CACHE_MUSCLE = "muscle";

  /** Cache name for all muscles. */
  public static final String CACHE_MUSCLES = "muscles";

  /** Cache name for Workout of the Day (WOD) data. */
  public static final String CACHE_WODS = "wods";

  /**
   * Configures the {@link RedisCacheManager} with custom serializers for specific DTOs.
   *
   * @param connectionFactory the Redis connection factory
   * @param properties validated cache configuration properties
   * @param objectMapper the Jackson object mapper for JSON serialization
   * @return a configured RedisCacheManager
   */
  @Bean
  public RedisCacheManager cacheManager(
      RedisConnectionFactory connectionFactory,
      CacheProperties properties,
      ObjectMapper objectMapper) {

    TypeFactory tf = objectMapper.getTypeFactory();

    var wodType = tf.constructType(WodResponse.class);
    var wodSerializer = new JacksonJsonRedisSerializer<>(objectMapper, wodType);

    var movementsType = tf.constructCollectionType(List.class, MovementSummaryResponse.class);
    var movementsSerializer = new JacksonJsonRedisSerializer<>(objectMapper, movementsType);

    var musclesType = tf.constructCollectionType(List.class, MuscleResponse.class);
    var musclesSerializer = new JacksonJsonRedisSerializer<>(objectMapper, musclesType);

    var singleMuscleType = tf.constructType(MuscleResponse.class);
    var singleMuscleSerializer = new JacksonJsonRedisSerializer<>(objectMapper, singleMuscleType);

    RedisCacheConfiguration baseConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMillis(properties.defaultTtl()))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()));

    Map<String, RedisCacheConfiguration> cacheConfigurations =
        Map.of(
            CACHE_WODS,
            baseConfig
                .entryTtl(Duration.ofMillis(properties.wodsTtl()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(wodSerializer)),
            CACHE_MOVEMENTS,
            baseConfig
                .entryTtl(Duration.ofMillis(properties.masterDataTtl()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        movementsSerializer)),
            CACHE_MUSCLES,
            baseConfig
                .entryTtl(Duration.ofMillis(properties.masterDataTtl()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(musclesSerializer)),
            CACHE_MUSCLE,
            baseConfig
                .entryTtl(Duration.ofMillis(properties.masterDataTtl()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        singleMuscleSerializer)));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(baseConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .build();
  }

  /**
   * Configuration properties for caching.
   *
   * @param defaultTtl Default time-to-live in milliseconds.
   * @param masterDataTtl TTL for master data (movements, muscles) in milliseconds.
   * @param wodsTtl TTL for WOD data in milliseconds.
   * @param keyPrefix Optional prefix for Redis keys.
   */
  @ConfigurationProperties(prefix = "application.cache")
  @Validated
  public record CacheProperties(
      @NotNull @Positive Long defaultTtl,
      @NotNull @Positive Long masterDataTtl,
      @NotNull @Positive Long wodsTtl,
      String keyPrefix) {}
}
