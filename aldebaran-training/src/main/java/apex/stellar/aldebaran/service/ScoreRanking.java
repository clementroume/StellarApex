package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import java.util.Comparator;
import java.util.function.Function;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;

/**
 * Single ordering of scores, shared by personal records, rank comparison and leaderboards.
 *
 * <p>Best first: the scaling tier ({@code ELITE > RX > SCALED > CUSTOM}), then the performance for
 * the WOD's score type. For TIME, every finished time beats every time-capped score, and capped
 * scores are ranked by the reps completed at the cap. Missing values rank last.
 *
 * <p>The optional tiebreak time only separates equal performances between athletes (compare,
 * leaderboard); a personal record never depends on it.
 */
public final class ScoreRanking {

  private ScoreRanking() {}

  /**
   * Orders scores of the same WOD, best first.
   *
   * @param type The WOD's score type.
   * @return A comparator placing the best score first.
   */
  public static Comparator<Score> bestFirst(ScoreType type) {
    return Comparator.<Score>comparingInt(s -> -s.getScaling().getTier())
        .thenComparing(performanceBestFirst(type));
  }

  /**
   * Orders scores of different athletes on the same WOD and scaling level: performance, then the
   * lower tiebreak time (scores without one after those with one).
   *
   * @param type The WOD's score type.
   * @return A comparator placing the best-ranked score first.
   */
  public static Comparator<Score> rankingFirst(ScoreType type) {
    return performanceBestFirst(type).thenComparing(ascending(Score::getTiebreakSeconds));
  }

  /**
   * Orders scores of the same WOD and scaling level by performance only, best first.
   *
   * @param type The WOD's score type.
   * @return A comparator placing the best performance first.
   */
  public static Comparator<Score> performanceBestFirst(ScoreType type) {
    return switch (type) {
      case TIME ->
          Comparator.comparing((Score s) -> Boolean.TRUE.equals(s.getTimeCapped()))
              .thenComparing(ascending(Score::getTimeSeconds))
              .thenComparing(descending(Score::getReps));
      case ROUNDS -> descending(Score::getRounds);
      case REPS -> descending(Score::getReps);
      case WEIGHT -> descending(Score::getMaxWeightKg);
      case LOAD -> descending(Score::getTotalLoadKg);
      case CALORIES -> descending(Score::getTotalCalories);
      case DISTANCE -> descending(Score::getTotalDistanceMeters);
      case NONE -> Comparator.comparing(Score::getDate, Comparator.reverseOrder());
    };
  }

  /**
   * Database sort matching {@link #rankingFirst(ScoreType)}, for paginated leaderboards.
   *
   * @param type The WOD's score type.
   * @return The sort to apply to a scores query.
   */
  public static Sort leaderboardSort(ScoreType type) {
    Sort performance =
        switch (type) {
          case TIME ->
              Sort.by(
                  Order.asc("timeCapped"),
                  Order.asc("timeSeconds").nullsLast(),
                  Order.desc("reps").nullsLast());
          case ROUNDS -> Sort.by(Order.desc("rounds").nullsLast());
          case REPS -> Sort.by(Order.desc("reps").nullsLast());
          case WEIGHT -> Sort.by(Order.desc("maxWeightKg").nullsLast());
          case LOAD -> Sort.by(Order.desc("totalLoadKg").nullsLast());
          case CALORIES -> Sort.by(Order.desc("totalCalories").nullsLast());
          case DISTANCE -> Sort.by(Order.desc("totalDistanceMeters").nullsLast());
          case NONE -> Sort.by(Order.desc("date"));
        };
    // Ties: the optional tiebreak time, then the earliest logged score, then a stable order
    return performance.and(
        Sort.by(Order.asc("tiebreakSeconds").nullsLast(), Order.asc("loggedAt"), Order.asc("id")));
  }

  private static <T extends Comparable<? super T>> Comparator<Score> ascending(
      Function<Score, T> value) {
    return Comparator.comparing(value, Comparator.nullsLast(Comparator.naturalOrder()));
  }

  private static <T extends Comparable<? super T>> Comparator<Score> descending(
      Function<Score, T> value) {
    return Comparator.comparing(value, Comparator.nullsLast(Comparator.reverseOrder()));
  }
}
