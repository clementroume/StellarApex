package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Measure;
import apex.stellar.aldebaran.model.enums.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a WOD line prescribes, read from its free text ({@code repsScheme}) and its measures (D121):
 * a number ({@code 21}), a scheme ({@code 21-15-9}, one value per round), {@code max}, a duration
 * ({@code 1 min}), an open ladder ({@code 3-6-9-...}), {@code N x M} ({@code 5 x 5}, {@code 8 x 20 s}),
 * or no text with a distance, calories or a duration. Anything else is unreadable: the line counts 0.
 *
 * @param measure Unit of the values.
 * @param kind How the values read.
 * @param values Values per round: one for a number, several for a scheme or {@code N x M} sets.
 * @param ladderStep Step of an open ladder (its start is the first value), else 0.
 */
record LinePrescription(Measure measure, Kind kind, List<Double> values, double ladderStep) {

  /** How a prescription reads. */
  public enum Kind {
    /** Fixed values per round. */
    FIXED,
    /** As many as possible: the score decides (D121 rule 3). */
    MAX,
    /** Ascending without end: the score decides (D121 rule 2). */
    OPEN_LADDER,
    /** Nothing readable (D121 rule 5). */
    UNREADABLE
  }

  private static final Pattern NUMBERS = Pattern.compile("^\\d+(\\s*-\\s*\\d+)*$");
  private static final Pattern OPEN_LADDER =
      Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)(\\s*-\\s*\\d+)*\\s*-\\s*(\\.\\.\\.|…)$");
  private static final Pattern DURATION =
      Pattern.compile("^(\\d+)\\s*(s|sec|secs|seconds?|min|mins|minutes?|mn)$");
  private static final Pattern TIMES =
      Pattern.compile("^(\\d+)\\s*[x×]\\s*(\\d+)\\s*(s|sec|secs|seconds?|min|mins|minutes?|mn)?$");

  private static final LinePrescription NONE =
      new LinePrescription(Measure.REPS, Kind.UNREADABLE, List.of(), 0);

  /**
   * Reads a line.
   *
   * @param line The line.
   * @param division The athlete's division (women's calories when given), or null.
   * @return Its prescription.
   */
  public static LinePrescription of(WodMovement line, @Nullable Division division) {
    String text =
        line.getRepsScheme() == null ? "" : line.getRepsScheme().trim().toLowerCase(Locale.ROOT);
    if (text.isEmpty()) {
      return measured(line, division);
    }
    if (text.equals("max")) {
      return new LinePrescription(
          line.getCalories() != null ? Measure.CALORIES : Measure.REPS, Kind.MAX, List.of(), 0);
    }
    Matcher duration = DURATION.matcher(text);
    if (duration.matches()) {
      return fixed(Measure.SECONDS, seconds(Integer.parseInt(duration.group(1)), duration.group(2)));
    }
    Matcher times = TIMES.matcher(text);
    if (times.matches()) {
      int count = Integer.parseInt(times.group(1));
      int each = Integer.parseInt(times.group(2));
      if (times.group(3) != null) {
        return fixed(Measure.SECONDS, count * seconds(each, times.group(3)));
      }
      List<Double> sets = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        sets.add((double) each);
      }
      return new LinePrescription(Measure.REPS, Kind.FIXED, sets, 0);
    }
    Matcher ladder = OPEN_LADDER.matcher(text);
    if (ladder.matches()) {
      double start = Double.parseDouble(ladder.group(1));
      double step = Double.parseDouble(ladder.group(2)) - start;
      return step > 0
          ? new LinePrescription(Measure.REPS, Kind.OPEN_LADDER, List.of(start), step)
          : NONE;
    }
    if (NUMBERS.matcher(text).matches()) {
      List<Double> values = new ArrayList<>();
      for (String part : text.split("-")) {
        values.add(Double.parseDouble(part.trim()));
      }
      return new LinePrescription(Measure.REPS, Kind.FIXED, values, 0);
    }
    return NONE;
  }

  /** No text: the distance, else the calories, else the duration of the line. */
  private static LinePrescription measured(WodMovement line, @Nullable Division division) {
    if (line.getDistance() != null && line.getDistance() > 0) {
      Unit unit = line.getDistanceUnit() != null ? line.getDistanceUnit() : Unit.METERS;
      return fixed(Measure.METERS, unit.toBase(line.getDistance()));
    }
    Integer calories =
        division == Division.WOMEN && line.getCaloriesFemale() != null
            ? line.getCaloriesFemale()
            : line.getCalories();
    if (calories != null && calories > 0) {
      return fixed(Measure.CALORIES, calories);
    }
    if (line.getDurationSeconds() != null && line.getDurationSeconds() > 0) {
      return fixed(Measure.SECONDS, line.getDurationSeconds());
    }
    return NONE;
  }

  private static LinePrescription fixed(Measure measure, double value) {
    return new LinePrescription(measure, Kind.FIXED, List.of(value), 0);
  }

  private static int seconds(int value, String unit) {
    return unit.startsWith("m") ? value * 60 : value;
  }

  /**
   * Whether the line can be counted at all.
   *
   * @return False for an unreadable line.
   */
  public boolean readable() {
    return kind != Kind.UNREADABLE;
  }

  /**
   * The value of round {@code k} (from 0) of a fixed or ladder prescription: a scheme gives one
   * value per round, a number the same value each round, a ladder grows by its step.
   *
   * @param k The round.
   * @return The value, 0 past the end of a scheme.
   */
  public double valueAt(int k) {
    return switch (kind) {
      case OPEN_LADDER -> values.getFirst() + k * ladderStep;
      case FIXED -> values.size() == 1 ? values.getFirst() : (k < values.size() ? values.get(k) : 0);
      default -> 0;
    };
  }

  /**
   * Whether the values are one per round of the WOD (a scheme such as {@code 21-15-9}).
   *
   * @return True for several fixed values.
   */
  public boolean isScheme() {
    return kind == Kind.FIXED && values.size() > 1;
  }

  /**
   * The sum of the values (a whole scheme, or {@code N x M}).
   *
   * @return The total.
   */
  public double total() {
    return values.stream().mapToDouble(Double::doubleValue).sum();
  }
}
