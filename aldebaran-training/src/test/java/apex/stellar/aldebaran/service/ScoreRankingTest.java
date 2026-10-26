package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class ScoreRankingTest {

  private static Score time(long id, ScalingLevel scaling, Integer seconds, Integer capReps) {
    return Score.builder()
        .id(id)
        .scaling(scaling)
        .timeSeconds(seconds)
        .timeCapped(capReps != null)
        .reps(capReps)
        .build();
  }

  @Test
  @DisplayName("bestFirst: tier, then finished times, then capped scores by reps")
  void bestFirst_Time() {
    Score scaledFast = time(1, ScalingLevel.SCALED, 100, null);
    Score rxCapped = time(2, ScalingLevel.RX, null, 80);
    Score rxCappedMore = time(3, ScalingLevel.RX, null, 90);
    Score rxSlow = time(4, ScalingLevel.RX, 900, null);
    Score eliteSlow = time(5, ScalingLevel.ELITE, 1000, null);

    List<Score> sorted =
        List.of(scaledFast, rxCapped, rxCappedMore, rxSlow, eliteSlow).stream()
            .sorted(ScoreRanking.bestFirst(ScoreType.TIME))
            .toList();

    assertEquals(List.of(eliteSlow, rxSlow, rxCappedMore, rxCapped, scaledFast), sorted);
  }

  @Test
  @DisplayName("performanceBestFirst: missing values rank last")
  void performance_NullsLast() {
    Score none = Score.builder().id(1L).scaling(ScalingLevel.RX).build();
    Score heavy = Score.builder().id(2L).scaling(ScalingLevel.RX).maxWeightKg(120.0).build();

    List<Score> sorted =
        List.of(none, heavy).stream()
            .sorted(ScoreRanking.performanceBestFirst(ScoreType.WEIGHT))
            .toList();

    assertEquals(List.of(heavy, none), sorted);
  }

  @Test
  @DisplayName("leaderboardSort: TIME puts finished first, then capped by reps, ties by log time")
  void leaderboardSort_Time() {
    Sort sort = ScoreRanking.leaderboardSort(ScoreType.TIME);

    assertEquals(
        Sort.by(
            Sort.Order.asc("timeCapped"),
            Sort.Order.asc("timeSeconds").nullsLast(),
            Sort.Order.desc("reps").nullsLast(),
            Sort.Order.asc("loggedAt"),
            Sort.Order.asc("id")),
        sort);
  }
}
