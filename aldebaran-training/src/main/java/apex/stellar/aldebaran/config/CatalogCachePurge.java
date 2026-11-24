package apex.stellar.aldebaran.config;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENT;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MOVEMENTS;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MUSCLE;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_MUSCLES;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_USUAL_NAMES;
import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Empties the catalog caches once the application is ready (lot E, D192). The catalog migrations
 * ({@code db/catalog}) may have changed muscles, movements or WODs behind the cache during this
 * start: entries cached by the previous version would otherwise live until their TTL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogCachePurge {

  /** The caches that hold catalog data. */
  static final List<String> CATALOG_CACHES =
      List.of(
          CACHE_MUSCLES, CACHE_MUSCLE, CACHE_MOVEMENTS, CACHE_MOVEMENT, CACHE_USUAL_NAMES,
          CACHE_WODS);

  private final CacheManager cacheManager;

  /** Clears every catalog cache; a Redis failure is logged and never stops the start. */
  @EventListener(ApplicationReadyEvent.class)
  public void purge() {
    try {
      CATALOG_CACHES.stream()
          .map(cacheManager::getCache)
          .filter(Objects::nonNull)
          .forEach(Cache::clear);
      log.info("Catalog caches cleared");
    } catch (RuntimeException e) {
      log.warn("Catalog caches not cleared: {}", e.getMessage());
    }
  }
}
