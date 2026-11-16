package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.PerSet;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Measure;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.service.LinePrescription.Kind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Equivalent sets of each line of a metcon (D121), before the team share (D128). Rules, the first
 * that answers: a readable prescription of a finished WOD; a quantity the score decides (AMRAP,
 * capped For Time, open ladder) unrolled in line order; {@code max} lines sharing the score; else
 * the work time, one set per minute shared by the lines' value in a round; nothing readable = 0.
 */
final class MetconVolume {

  /** Tabata defaults: 8 rounds of 20 s work and 10 s rest. */
  static final int TABATA_ROUNDS = 8;

  static final int TABATA_WORK = 20;
  static final int TABATA_REST = 10;

  /** Guard against a round that consumes no rep of the score. */
  private static final int MAX_ROUNDS = 2000;

  private MetconVolume() {}

  /** A line and how it reads. */
  private record Line(WodMovement line, LinePrescription prescription, PerSet perSet) {

    /**
     * Sets of an amount in the line's unit. Repetitions over a course length (a shuttle run, one rep
     * = out and back) count their meters against the meters of one set, so a longer shuttle weighs
     * more than the usual 15 m (450 m = 30 reps); without a meters value they count as reps.
     */
    double sets(double amount) {
      double meters = metersPerRep();
      if (meters > 0) {
        return amount * meters / perSet.meters();
      }
      Integer value = prescription.measure().of(perSet);
      return value == null || value <= 0 ? 0 : amount / value;
    }

    private double metersPerRep() {
      if (prescription.measure() != Measure.REPS
          || line.getLength() == null
          || line.getLength() <= 0
          || perSet.meters() == null
          || perSet.meters() <= 0) {
        return 0;
      }
      Unit unit = line.getLengthUnit() != null ? line.getLengthUnit() : Unit.METERS;
      return 2 * unit.toBase(line.getLength());
    }
  }

  /**
   * Equivalent sets per line.
   *
   * @param wod The WOD, with its lines and their movements.
   * @param score The score, or null for a block checked without score (prescription).
   * @param division The athlete's division (women's calories), or null.
   * @return Sets per line, in line order; unreadable lines count 0.
   */
  static Map<WodMovement, Double> sets(Wod wod, @Nullable Score score, @Nullable Division division) {
    List<Line> lines = new ArrayList<>();
    for (WodMovement line :
        wod.getMovements().stream().sorted(BalanceCalculator.LINE_ORDER).toList()) {
      lines.add(new Line(line, prescription(wod, line, division), CatalogRules.perSet(line)));
    }
    Map<WodMovement, Double> sets = new LinkedHashMap<>();
    lines.forEach(l -> sets.put(l.line(), 0.0));
    if (lines.isEmpty()) {
      return sets;
    }
    if (wod.getWodType() == WodType.TABATA) {
      int seconds = tabataRounds(wod) * (wod.getWorkSeconds() != null ? wod.getWorkSeconds() : TABATA_WORK);
      for (Line l : lines) {
        double own =
            l.prescription().measure() == Measure.SECONDS && l.prescription().readable()
                ? l.prescription().total()
                : seconds;
        Integer perSet = Measure.SECONDS.of(l.perSet());
        sets.put(l.line(), perSet == null ? 0 : own / perSet);
      }
      return sets;
    }
    int rounds = rounds(wod, score);
    Integer scoreReps = scoreReps(score);
    List<Line> maxLines = lines.stream().filter(l -> l.prescription().kind() == Kind.MAX).toList();
    boolean dependsOnScore =
        wod.getWodType() == WodType.AMRAP
            || (wod.getWodType() == WodType.FOR_TIME
                && score != null
                && Boolean.TRUE.equals(score.getTimeCapped()))
            || (wod.getWodType() != WodType.EMOM
                && lines.stream().anyMatch(l -> l.prescription().kind() == Kind.OPEN_LADDER));

    if (!maxLines.isEmpty()) {
      // Rule 3: the score's reps shared equally by the max lines; the others as prescribed
      for (Line l : lines) {
        if (l.prescription().kind() == Kind.MAX) {
          sets.put(l.line(), scoreReps == null ? 0 : l.sets((double) scoreReps / maxLines.size()));
        } else {
          sets.put(l.line(), l.sets(prescribed(l, rounds)));
        }
      }
      if (scoreReps == null && wod.getWodType() == WodType.AMRAP) {
        byWorkTime(wod, score, rounds, lines, sets);
      }
      return sets;
    }
    if (dependsOnScore) {
      if (scoreReps == null || !unroll(wod, rounds, scoreReps, lines, sets)) {
        byWorkTime(wod, score, rounds, lines, sets);
      }
      return sets;
    }
    for (Line l : lines) {
      sets.put(l.line(), l.sets(prescribed(l, rounds)));
    }
    return sets;
  }

  /** How a line reads; a Death by line without text climbs the WOD's ladder. */
  static LinePrescription prescription(
      Wod wod, WodMovement line, @Nullable Division division) {
    LinePrescription read = LinePrescription.of(line, division);
    if (!read.readable() && wod.getLadderStart() != null) {
      int step = wod.getLadderStep() != null ? wod.getLadderStep() : wod.getLadderStart();
      return new LinePrescription(Measure.REPS, Kind.OPEN_LADDER, List.of((double) wod.getLadderStart()), step);
    }
    return read;
  }

  /** The prescribed amount of a line over the WOD (rule 1). */
  private static double prescribed(Line l, int rounds) {
    LinePrescription p = l.prescription();
    if (!p.readable() || p.kind() == Kind.MAX) {
      return 0;
    }
    if (p.isScheme()) {
      return p.total();
    }
    if (p.kind() == Kind.OPEN_LADDER) {
      double total = 0;
      for (int k = 0; k < rounds; k++) {
        total += p.valueAt(k);
      }
      return total;
    }
    boolean once = l.line().getSegment() != null && l.line().getSegment() != Segment.MAIN;
    return p.total() * (once ? 1 : rounds);
  }

  /**
   * Rule 2: the score's reps walked through the lines in order, whole rounds then the remainder. A
   * calorie is a rep; a distance or time line is worth the rest of the round ({@code repsPerRound}
   * minus the other lines).
   *
   * @return False when the score cannot be unrolled (a round worth no rep).
   */
  private static boolean unroll(
      Wod wod, int rounds, int scoreReps, List<Line> lines, Map<WodMovement, Double> sets) {
    List<Line> buyIn = segment(lines, Segment.BUY_IN);
    List<Line> main = segment(lines, Segment.MAIN);
    List<Line> buyOut = segment(lines, Segment.BUY_OUT);
    boolean endless = wod.getWodType() == WodType.AMRAP;
    int schemeRounds =
        main.stream().mapToInt(l -> l.prescription().isScheme() ? l.prescription().values().size() : 0).max().orElse(0);
    int roundCount = endless ? MAX_ROUNDS : Math.max(rounds, schemeRounds);
    double remaining = scoreReps;
    remaining = walk(buyIn, 0, wod, remaining, sets);
    for (int k = 0; k < roundCount && remaining > 0; k++) {
      double before = remaining;
      remaining = walk(main, k, wod, remaining, sets);
      if (remaining > 0 && remaining == before) {
        return false;
      }
    }
    if (!endless && remaining > 0) {
      walk(buyOut, 0, wod, remaining, sets);
    }
    return true;
  }

  private static double walk(
      List<Line> lines, int k, Wod wod, double remaining, Map<WodMovement, Double> sets) {
    double repsOfRound = 0;
    int measured = 0;
    for (Line l : lines) {
      if (counted(l.prescription().measure())) {
        repsOfRound += l.prescription().valueAt(k);
      } else {
        measured++;
      }
    }
    double measuredValue =
        wod.getRepsPerRound() == null || measured == 0
            ? 0
            : Math.max(0, (wod.getRepsPerRound() - repsOfRound) / measured);
    for (Line l : lines) {
      if (remaining <= 0) {
        break;
      }
      double amount = l.prescription().valueAt(k);
      double worth = counted(l.prescription().measure()) ? amount : measuredValue;
      double part = worth <= 0 || worth <= remaining ? 1 : remaining / worth;
      sets.merge(l.line(), l.sets(amount * part), Double::sum);
      remaining -= worth * part;
    }
    return remaining;
  }

  private static boolean counted(Measure measure) {
    return measure == Measure.REPS || measure == Measure.CALORIES;
  }

  /**
   * Rule 4: one set per minute of work, shared by the lines' value in a round (AMRAP checked
   * without a score, capped work without reps).
   */
  private static void byWorkTime(
      Wod wod, @Nullable Score score, int rounds, List<Line> lines, Map<WodMovement, Double> sets) {
    double minutes = workSeconds(wod, score, rounds) / 60.0;
    double total = 0;
    Map<WodMovement, Double> weight = new LinkedHashMap<>();
    for (Line l : lines) {
      double w = l.prescription().readable() ? l.sets(l.prescription().valueAt(0)) : 0;
      weight.put(l.line(), w);
      total += w;
    }
    for (Line l : lines) {
      double share = total > 0 ? weight.get(l.line()) / total : 1.0 / lines.size();
      sets.put(l.line(), minutes * share);
    }
  }

  /**
   * The work time of a WOD, in seconds.
   *
   * @param wod The WOD.
   * @param score Its score, or null.
   * @param rounds The rounds done.
   * @return Seconds of work, 0 when the format gives none.
   */
  static int workSeconds(Wod wod, @Nullable Score score, int rounds) {
    return switch (wod.getWodType()) {
      case AMRAP -> wod.getTimeCapSeconds() != null ? wod.getTimeCapSeconds() : 0;
      case EMOM -> rounds * (wod.getEmomInterval() != null ? wod.getEmomInterval() : 60);
      case TABATA ->
          tabataRounds(wod)
              * ((wod.getWorkSeconds() != null ? wod.getWorkSeconds() : TABATA_WORK)
                  + (wod.getRestSeconds() != null ? wod.getRestSeconds() : TABATA_REST));
      case FOR_TIME -> {
        if (score != null && Boolean.TRUE.equals(score.getTimeCapped()) && wod.getTimeCapSeconds() != null) {
          yield wod.getTimeCapSeconds();
        }
        yield score != null && score.getTimeSeconds() != null ? score.getTimeSeconds() : 0;
      }
      default -> 0;
    };
  }

  static int tabataRounds(Wod wod) {
    return wod.getRounds() != null && wod.getRounds() > 0 ? wod.getRounds() : TABATA_ROUNDS;
  }

  /**
   * Rounds of a WOD: done on the minute for an EMOM scored in rounds, else prescribed.
   *
   * @param wod The WOD.
   * @param score Its score, or null.
   * @return The rounds, at least 1.
   */
  static int rounds(Wod wod, @Nullable Score score) {
    if (wod.getWodType() == WodType.EMOM && wod.getStructure() != WodStructure.INTERVALS) {
      if (score != null && wod.getScoreType() == ScoreType.ROUNDS && score.getRounds() != null) {
        return Math.max(0, score.getRounds());
      }
      if (wod.getEmomRounds() != null && wod.getEmomRounds() > 0) {
        return wod.getEmomRounds();
      }
      if (wod.getTimeCapSeconds() != null && wod.getEmomInterval() != null && wod.getEmomInterval() > 0) {
        return wod.getTimeCapSeconds() / wod.getEmomInterval();
      }
    }
    return wod.getRounds() != null && wod.getRounds() > 0 ? wod.getRounds() : 1;
  }

  /** Reps of a score: its reps, else its calories (a calorie is a rep). */
  private static @Nullable Integer scoreReps(@Nullable Score score) {
    if (score == null) {
      return null;
    }
    return score.getReps() != null ? score.getReps() : score.getTotalCalories();
  }

  private static List<Line> segment(List<Line> lines, Segment segment) {
    return lines.stream()
        .filter(l -> (l.line().getSegment() == null ? Segment.MAIN : l.line().getSegment()) == segment)
        .toList();
  }
}
