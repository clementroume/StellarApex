package apex.stellar.aldebaran.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
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
public class CacheConfig {

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

    // ObjectMapper
    ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    // Serializer Redis
    GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(mapper);

    // Default configuration (10 minutes)
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer));

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        // Master Data (quasi-immutable) → 24h
        .withCacheConfiguration("movements", defaultConfig.entryTtl(Duration.ofHours(24)))
        .withCacheConfiguration(
            "movements-by-category", defaultConfig.entryTtl(Duration.ofHours(24)))
        .withCacheConfiguration("muscles", defaultConfig.entryTtl(Duration.ofHours(24)))

        // WODs (occasionally modified) → 1h
        .withCacheConfiguration("wods", defaultConfig.entryTtl(Duration.ofHours(1)))

        // Scores (frequently added) → 5 min
        .withCacheConfiguration("wod-scores", defaultConfig.entryTtl(Duration.ofMinutes(5)))
        .build();
  }
}
