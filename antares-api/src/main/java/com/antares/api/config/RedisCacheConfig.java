package com.antares.api.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Configuration class for setting up Redis caching in the application. This includes both a
 * Spring-native CacheManager for use with Spring's caching abstraction and a JCache-compliant
 * CacheManager for third-party libraries that require JCache support.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

  /**
   * Configures a Spring-native CacheManager that uses Redis as the underlying cache store. This is
   * primarily used with Spring's @Cacheable abstraction.
   *
   * @param redisConnectionFactory The factory to create Redis connections, auto-configured by
   *     Spring Boot.
   * @return A RedisCacheManager instance.
   */
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

    RedisCacheConfiguration config =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues();

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(config)
        .withCacheConfiguration("refreshTokens", config.entryTtl(Duration.ofDays(7)))
        .build();
  }
}
