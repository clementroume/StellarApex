package apex.stellar.aldebaran.repository;

import apex.stellar.aldebaran.model.entities.BalanceWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Stored weeks of the balance (lot D, D120). Writes are upserts and stale marks (a write elsewhere
 * only marks a week; the display rebuilds it). Stale marks run inside other writes (scores,
 * journals): they never clear the persistence context; the rebuild's upsert does.
 */
@Repository
public interface BalanceWeekRepository extends JpaRepository<BalanceWeek, BalanceWeek.Key> {

  /**
   * An athlete's stored weeks, oldest first.
   *
   * @param userId The athlete.
   * @param from First Monday.
   * @param to Last Monday.
   * @return The weeks found.
   */
  @Query(
      "SELECT w FROM BalanceWeek w WHERE w.id.userId = :userId"
          + " AND w.id.weekStart BETWEEN :from AND :to ORDER BY w.id.weekStart")
  List<BalanceWeek> findWeeks(Long userId, LocalDate from, LocalDate to);

  /**
   * Serializes the rebuilds of one athlete's weeks until the end of the transaction. Its own key
   * (hashed with "balance"), apart from the records' lock on the plain user id.
   *
   * @param userId The athlete.
   * @return Nothing useful.
   */
  @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended('balance', :userId))", nativeQuery = true)
  Object lockAthlete(Long userId);

  /**
   * Marks a week to rebuild, creating its row when the week was never stored.
   *
   * @param userId The athlete.
   * @param weekStart Its Monday.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO aldebaran.balance_weeks (user_id, week_start, stale) VALUES (:userId, :weekStart, TRUE)"
              + " ON CONFLICT (user_id, week_start) DO UPDATE SET stale = TRUE",
      nativeQuery = true)
  void markStale(Long userId, LocalDate weekStart);

  /**
   * Marks every stored week of an athlete after a Monday (a record changed, D124).
   *
   * @param userId The athlete.
   * @param weekStart The Monday.
   */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE BalanceWeek w SET w.stale = true WHERE w.id.userId = :userId AND w.id.weekStart > :weekStart")
  void markStaleAfter(Long userId, LocalDate weekStart);

  /** Marks every stored week (the catalog or the rules changed). */
  @Modifying(flushAutomatically = true)
  @Query("UPDATE BalanceWeek w SET w.stale = true")
  void markAllStale();

  /**
   * Marks the weeks of the journals that checked a block of a WOD (its prescription changed).
   *
   * @param wodId The WOD.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "UPDATE aldebaran.balance_weeks b SET stale = TRUE WHERE EXISTS ("
              + " SELECT 1 FROM aldebaran.session_logs l"
              + " JOIN aldebaran.session_log_blocks lb ON lb.log_id = l.id AND lb.done"
              + " JOIN aldebaran.session_blocks sb ON sb.id = lb.block_id"
              + " WHERE sb.wod_id = :wodId AND l.user_id = b.user_id"
              + " AND date_trunc('week', l.date)::date = b.week_start)",
      nativeQuery = true)
  void markStaleForWod(Long wodId);

  /**
   * Marks the weeks of every journal of a session (its blocks changed).
   *
   * @param sessionId The session.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "UPDATE aldebaran.balance_weeks b SET stale = TRUE WHERE EXISTS ("
              + " SELECT 1 FROM aldebaran.session_logs l WHERE l.session_id = :sessionId"
              + " AND l.user_id = b.user_id AND date_trunc('week', l.date)::date = b.week_start)",
      nativeQuery = true)
  void markStaleForSession(Long sessionId);

  /**
   * Stores the totals of a week, fresh, with the current rules version.
   *
   * @param userId The athlete.
   * @param weekStart Its Monday.
   * @param rulesVersion The rules version.
   * @param totalSets Equivalent sets.
   * @param weightlifting Sets in weightlifting.
   * @param gymnastics Sets in gymnastics.
   * @param monostructural Sets in monostructural.
   * @param veryShort Seconds of very short metcons.
   * @param shortSeconds Seconds of short metcons.
   * @param medium Seconds of medium metcons.
   * @param longSeconds Seconds of long metcons.
   * @param intervals Seconds of intervals.
   * @param computedAt When.
   */
  @SuppressWarnings("java:S107") // One column per parameter
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO aldebaran.balance_weeks (user_id, week_start, rules_version, stale, total_sets,"
              + " weightlifting, gymnastics, monostructural, very_short_s, short_s, medium_s, long_s,"
              + " intervals_s, computed_at) VALUES (:userId, :weekStart, :rulesVersion, FALSE, :totalSets,"
              + " :weightlifting, :gymnastics, :monostructural, :veryShort, :shortSeconds, :medium,"
              + " :longSeconds, :intervals, :computedAt)"
              + " ON CONFLICT (user_id, week_start) DO UPDATE SET rules_version = :rulesVersion,"
              + " stale = FALSE, total_sets = :totalSets, weightlifting = :weightlifting,"
              + " gymnastics = :gymnastics, monostructural = :monostructural,"
              + " very_short_s = :veryShort, short_s = :shortSeconds, medium_s = :medium,"
              + " long_s = :longSeconds, intervals_s = :intervals, computed_at = :computedAt",
      nativeQuery = true)
  void upsert(
      Long userId,
      LocalDate weekStart,
      int rulesVersion,
      double totalSets,
      double weightlifting,
      double gymnastics,
      double monostructural,
      int veryShort,
      int shortSeconds,
      int medium,
      int longSeconds,
      int intervals,
      LocalDateTime computedAt);
}
