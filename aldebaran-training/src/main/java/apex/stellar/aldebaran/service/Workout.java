package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.ScoreLine;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One piece of work of an athlete, as the weekly balance reads it (D82): a score, or a session block
 * checked in a journal without a score (its prescription).
 *
 * @param wod The WOD, with its lines and movements.
 * @param score The score, or null for a checked block.
 * @param date The day it counts on (score date, else journal date).
 * @param blockKind Kind of the session block it belongs to, or null outside a session.
 * @param division The athlete's division (women's calories), or null.
 * @param teamMembers Members of the team that logged it, 0 when not a team score.
 * @param scaled Without a score: read the WOD's official Scaled version instead of the RX
 *     prescription (lot D', D162); a line the version keeps stays RX.
 */
public record Workout(
    Wod wod,
    @Nullable Score score,
    LocalDate date,
    @Nullable Kind blockKind,
    @Nullable Division division,
    int teamMembers,
    boolean scaled) {

  /** A workout done or checked by an athlete (lot D): the RX prescription without a score. */
  public Workout(
      Wod wod,
      @Nullable Score score,
      LocalDate date,
      @Nullable Kind blockKind,
      @Nullable Division division,
      int teamMembers) {
    this(wod, score, date, blockKind, division, teamMembers, false);
  }

  /**
   * A prescribed block of a programming (lot D'): no score, no athlete (men's calories, the
   * prescribed team size).
   *
   * @param wod The WOD.
   * @param date The day of its session.
   * @param blockKind The kind of its block.
   * @param scaled True for the official Scaled version.
   * @return The workout.
   */
  public static Workout prescribed(Wod wod, LocalDate date, Kind blockKind, boolean scaled) {
    return new Workout(wod, null, date, blockKind, null, 0, scaled);
  }

  /**
   * The lines as done (lot S, D148): the WOD's lines in the order the balance reads them, each
   * with the variant and the quantities the athlete changed (a substitute, reduced reps, a real
   * load). A "max" line keeps its "max": the reps counted on it are in {@link #maxCounts()}.
   *
   * @return The lines done, the prescription without a score.
   */
  public List<WodMovement> lines() {
    List<WodMovement> rx = wod.getMovements().stream().sorted(BalanceCalculator.LINE_ORDER).toList();
    if (score == null && scaled) {
      return rx.stream().map(LineVersions::scaled).toList();
    }
    if (score == null || score.getLines() == null || score.getLines().isEmpty()) {
      return rx;
    }
    return rx.stream()
        .map(
            line -> {
              ScoreLine changed = LineVersions.lineOf(score.getLines(), line);
              WodMovement done = LineVersions.done(line, changed);
              if (done != line && isMax(line) && changed.getRepsScheme() != null) {
                done.setRepsScheme(line.getRepsScheme());
              }
              return done;
            })
        .toList();
  }

  /**
   * The reps counted on "max" lines (D147), by WOD line ID.
   *
   * @return The counts, empty without a score or a count.
   */
  public Map<Long, Integer> maxCounts() {
    Map<Long, Integer> counts = new HashMap<>();
    if (score == null || score.getLines() == null) {
      return counts;
    }
    for (ScoreLine changed : score.getLines()) {
      WodMovement line = changed.getWodMovement();
      String text = changed.getRepsScheme();
      if (line != null && isMax(line) && text != null && text.trim().matches("\\d+")) {
        counts.put(line.getId(), Integer.valueOf(text.trim()));
      }
    }
    return counts;
  }

  private static boolean isMax(WodMovement line) {
    return line.getRepsScheme() != null
        && "max".equals(line.getRepsScheme().trim().toLowerCase(Locale.ROOT));
  }
}
