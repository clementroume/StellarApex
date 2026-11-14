package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.config.RedisCacheConfig.CACHE_WODS;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.BalanceWeekRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.LocalDate;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the stored weeks of the balance honest (D120): every write that changes what an athlete did
 * only marks the weeks to rebuild; the rebuild happens on the next display.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceMaintenanceService {

  private final BalanceWeekRepository weekRepository;
  private final WodRepository wodRepository;
  private final CacheManager cacheManager;

  /**
   * A score was logged, corrected, re-dated or deleted. On a WOD that feeds the records, the later
   * weeks are marked too: "near the record" reads the record of each day (D124).
   *
   * @param userIds The athletes of the score (a whole team).
   * @param date The day of the score (call once per day when re-dated).
   * @param wod Its WOD.
   */
  public void scoreChanged(Collection<Long> userIds, @Nullable LocalDate date, Wod wod) {
    if (date == null) {
      return;
    }
    LocalDate monday = WeeklyBalanceService.monday(date);
    boolean feedsRecords = LiftVariant.of(wod).isPresent();
    for (Long userId : userIds) {
      if (feedsRecords) {
        weekRepository.markStale(userId, monday);
        weekRepository.markStaleAfter(userId, monday);
      } else {
        weekRepository.markStale(userId, monday);
      }
    }
  }

  /**
   * A journal was created, deleted, or one of its blocks checked or unchecked.
   *
   * @param userId The athlete.
   * @param date The day of the journal.
   */
  public void journalChanged(Long userId, @Nullable LocalDate date) {
    if (date != null) {
      weekRepository.markStale(userId, WeeklyBalanceService.monday(date));
    }
  }

  /**
   * A WOD's prescription changed: the journals that checked one of its blocks count it again.
   *
   * @param wodId The WOD.
   */
  public void wodChanged(Long wodId) {
    weekRepository.markStaleForWod(wodId);
  }

  /**
   * A session's blocks changed (a block added, removed, or borrowing another WOD).
   *
   * @param sessionId The session.
   */
  public void sessionChanged(Long sessionId) {
    weekRepository.markStaleForSession(sessionId);
  }

  /** The catalog changed (roles, values of a set, modality): every week is rebuilt. */
  public void catalogChanged() {
    weekRepository.markAllStale();
  }

  /**
   * Marks every week to rebuild and recomputes the modalities of every WOD with the rules of today
   * (calibration during the beta, D120, D127).
   */
  @Transactional
  public void recomputeAll() {
    weekRepository.markAllStale();
    int changed = 0;
    for (Wod wod : wodRepository.findAll()) {
      var modalities = CatalogRules.modalities(wod.getMovements());
      if (!modalities.equals(wod.getModalities())) {
        wod.getModalities().clear();
        wod.getModalities().addAll(modalities);
        changed++;
      }
    }
    Cache wods = cacheManager.getCache(CACHE_WODS);
    if (wods != null) {
      wods.clear();
    }
    log.info("Balance recompute: every week marked stale, modalities changed on {} WODs", changed);
  }
}
