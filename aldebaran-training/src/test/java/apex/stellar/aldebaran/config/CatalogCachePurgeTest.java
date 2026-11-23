package apex.stellar.aldebaran.config;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class CatalogCachePurgeTest {

  @Test
  @DisplayName("purge: clears every catalog cache the manager knows")
  void purge_clearsCatalogCaches() {
    CacheManager manager = mock(CacheManager.class);
    Cache wods = mock(Cache.class);
    Cache muscles = mock(Cache.class);
    when(manager.getCache(RedisCacheConfig.CACHE_WODS)).thenReturn(wods);
    when(manager.getCache(RedisCacheConfig.CACHE_MUSCLES)).thenReturn(muscles);

    new CatalogCachePurge(manager).purge();

    verify(wods).clear();
    verify(muscles).clear();
  }

  @Test
  @DisplayName("purge: a Redis failure never stops the start")
  void purge_toleratesFailure() {
    CacheManager manager = mock(CacheManager.class);
    Cache wods = mock(Cache.class);
    when(manager.getCache(RedisCacheConfig.CACHE_MUSCLES)).thenReturn(wods);
    doThrow(new IllegalStateException("down")).when(wods).clear();

    new CatalogCachePurge(manager).purge();

    verify(wods).clear();
  }
}
