package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleRegion;
import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import apex.stellar.aldebaran.service.ProgrammingAlerts.Alert;
import apex.stellar.aldebaran.service.ProgrammingAlerts.Kind;
import apex.stellar.aldebaran.service.ProgrammingAlerts.MuscleInfo;
import apex.stellar.aldebaran.service.ProgrammingAlerts.RegionGap;
import apex.stellar.aldebaran.service.ProgrammingAlerts.Trend;
import apex.stellar.aldebaran.service.ProgrammingAlerts.UnusualSession;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProgrammingAlerts (lot D', D173-D181)")
class ProgrammingAlertsTest {

  /** A small catalog: one muscle per main group, by code. */
  static final Map<Long, MuscleInfo> MUSCLES = new HashMap<>();
  static final Map<String, Long> IDS = new HashMap<>();

  static {
    muscle(1, "quadriceps-femoris", MuscleRegion.THIGH_LEG, null);
    muscle(2, "biceps-femoris", MuscleRegion.THIGH_LEG, "musculi-ischiocrurales");
    muscle(3, "gluteus-maximus", MuscleRegion.HIP, "musculi-glutei");
    muscle(4, "latissimus-dorsi", MuscleRegion.BACK, null);
    muscle(5, "pectoralis-major-pars-clavicularis", MuscleRegion.THORAX, "pectoralis-major");
    muscle(6, "pectoralis-major-pars-sternocostalis", MuscleRegion.THORAX, "pectoralis-major");
    muscle(7, "deltoideus-pars-acromialis", MuscleRegion.SHOULDER, "deltoideus");
    muscle(8, "triceps-brachii", MuscleRegion.ARM_FOREARM, null);
    muscle(9, "rectus-abdominis", MuscleRegion.ABDOMEN_PELVIS, "musculi-abdominis");
    muscle(10, "erector-spinae", MuscleRegion.BACK, null);
  }

  static void muscle(long id, String code, MuscleRegion region, String group) {
    MUSCLES.put(id, new MuscleInfo(code, region, group));
    IDS.put(code, id);
  }

  /** Volumes from code → moving sets. */
  static WeekTotals totals(Object... codeAndSets) {
    WeekTotals t = new WeekTotals();
    for (int i = 0; i < codeAndSets.length; i += 2) {
      t.addMuscle(IDS.get((String) codeAndSets[i]), ((Number) codeAndSets[i + 1]).doubleValue(), 0);
    }
    return t;
  }

  /** Adds lines with one pattern each: pattern → sets. */
  static WeekTotals lines(WeekTotals t, Object... patternAndSets) {
    for (int i = 0; i < patternAndSets.length; i += 2) {
      t.addPatterns(Set.of((MovementPattern) patternAndSets[i]), ((Number) patternAndSets[i + 1]).doubleValue());
    }
    return t;
  }

  /** Four weeks where every main group gets 6 sets per week. */
  static WeekTotals fourGoodWeeks() {
    return totals(
        "quadriceps-femoris", 24, "biceps-femoris", 24, "gluteus-maximus", 24, "latissimus-dorsi", 24,
        "pectoralis-major-pars-clavicularis", 12, "pectoralis-major-pars-sternocostalis", 12,
        "deltoideus-pars-acromialis", 24, "triceps-brachii", 24);
  }

  @Test
  @DisplayName("Thresholds stay as decided (D176, D180, D181)")
  void thresholds() {
    assertThat(ProgrammingAlerts.PATTERN_GAP).isEqualTo(0.6);
    assertThat(ProgrammingAlerts.PATTERN_FLOOR).isEqualTo(5.0);
    assertThat(ProgrammingAlerts.REGULAR_MAX_MISS).isEqualTo(0.10);
    assertThat(ProgrammingAlerts.GROUP_MIN_SETS).isEqualTo(4.0);
    assertThat(ProgrammingAlerts.MAIN_GROUPS)
        .hasSize(7)
        .doesNotContain("erector-spinae", "musculi-abdominis");
    assertThat(ProgrammingAlerts.UNUSUAL_RATIO).isEqualTo(1.3);
    assertThat(ProgrammingAlerts.UNUSUAL_HISTORY_WEEKS).isEqualTo(4);
    assertThat(ProgrammingAlerts.MAX_ALERTS).isEqualTo(3);
  }

  @Test
  @DisplayName("A line counts once per axis; its details may add up to more than the axis")
  void axisShares() {
    WeekTotals t = new WeekTotals();
    t.addPatterns(EnumSet.of(MovementPattern.PULL_V, MovementPattern.CORE_FLEXION, MovementPattern.CORE_EXTENSION), 10);
    t.addPatterns(EnumSet.of(MovementPattern.SQUAT), 20);

    Map<Axis, Double> shares = ProgrammingAlerts.axisShares(t);
    assertThat(shares.get(Axis.SQUAT)).isCloseTo(50, within(1e-9));
    assertThat(shares.get(Axis.PULL)).isCloseTo(25, within(1e-9));
    assertThat(shares.get(Axis.CORE)).isCloseTo(25, within(1e-9));
    assertThat(shares.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(100, within(1e-9));

    Map<MovementPattern, Double> details = ProgrammingAlerts.detailShares(t);
    assertThat(details.get(MovementPattern.CORE_FLEXION) + details.get(MovementPattern.CORE_EXTENSION))
        .isCloseTo(50, within(1e-9));
    assertThat(ProgrammingAlerts.axisShares(new WeekTotals())).containsEntry(Axis.CORE, 0.0);
  }

  @Test
  @DisplayName("Pattern gap: a judged axis under 0.6 times its full-week share (D174, D176)")
  void patternGaps() {
    Map<Axis, Double> full = new EnumMap<>(Map.of(Axis.SQUAT, 20.0, Axis.CORE, 15.0, Axis.LUNGE, 10.0));
    Map<Axis, Double> profile = new EnumMap<>(Map.of(Axis.SQUAT, 11.0, Axis.CORE, 9.5, Axis.LUNGE, 2.0));

    List<Alert> alerts = ProgrammingAlerts.patternGaps(profile, full, EnumSet.of(Axis.SQUAT, Axis.CORE));
    assertThat(alerts).singleElement().satisfies(a -> {
      assertThat(a.kind()).isEqualTo(Kind.PATTERN_GAP);
      assertThat(a.subject()).isEqualTo("SQUAT");
      assertThat(a.limit()).isCloseTo(12, within(1e-9));
      assertThat(a.severity()).isGreaterThan(1);
    });
    // Core above 0.6 times is not a gap; Lunge is not judged (irregular or under the floor)
    assertThat(alerts).noneMatch(a -> a.subject().equals("CORE") || a.subject().equals("LUNGE"));
  }

  @Test
  @DisplayName("Group minimum: under 4 sets per week, only with 3 trained weeks (D180)")
  void groupMinimum() {
    assertThat(ProgrammingAlerts.groupMinimum(fourGoodWeeks(), 4, 4, MUSCLES)).isEmpty();

    WeekTotals fewHamstrings = fourGoodWeeks();
    fewHamstrings.addMuscle(IDS.get("biceps-femoris"), -10, 0); // 14 over 4 weeks: 3.5 per week
    assertThat(ProgrammingAlerts.groupMinimum(fewHamstrings, 4, 4, MUSCLES))
        .singleElement()
        .satisfies(a -> {
          assertThat(a.subject()).isEqualTo("musculi-ischiocrurales");
          assertThat(a.value()).isCloseTo(3.5, within(1e-9));
          assertThat(a.limit()).isEqualTo(4.0);
        });
    assertThat(ProgrammingAlerts.groupMinimum(fewHamstrings, 4, 2, MUSCLES)).isEmpty();
    // A group sums its members: the two heads of the pectoralis major
    assertThat(ProgrammingAlerts.groupSets(fourGoodWeeks(), MUSCLES, "pectoralis-major")).isEqualTo(24);
  }

  @Test
  @DisplayName("Regions against the full week are information: behind, in line, ahead (D180)")
  void regionGaps() {
    Map<MuscleRegion, Double> full = Map.of(MuscleRegion.THIGH_LEG, 40.0, MuscleRegion.SHOULDER, 10.0, MuscleRegion.BACK, 20.0);
    Map<MuscleRegion, Double> profile = Map.of(MuscleRegion.THIGH_LEG, 30.0, MuscleRegion.SHOULDER, 13.0, MuscleRegion.BACK, 21.0);

    Map<MuscleRegion, Trend> trends = new EnumMap<>(MuscleRegion.class);
    for (RegionGap gap : ProgrammingAlerts.regionGaps(profile, full)) {
      trends.put(gap.region(), gap.trend());
    }
    assertThat(trends)
        .containsEntry(MuscleRegion.THIGH_LEG, Trend.BEHIND)
        .containsEntry(MuscleRegion.SHOULDER, Trend.AHEAD)
        .containsEntry(MuscleRegion.BACK, Trend.IN_LINE)
        .containsEntry(MuscleRegion.THORAX, Trend.IN_LINE);
    assertThat(ProgrammingAlerts.regionGaps(profile, full).getFirst().region()).isEqualTo(MuscleRegion.THIGH_LEG);
  }

  @Test
  @DisplayName("Unusual session: above 1.3 times the biggest day of 4 programmed weeks before (D173, D180)")
  void unusualSessions() {
    LocalDate monday = LocalDate.of(2026, 11, 16);
    Map<LocalDate, WeekTotals> days = new HashMap<>();
    for (int w = 1; w <= 4; w++) {
      days.put(monday.minusWeeks(w), totals("deltoideus-pars-acromialis", 4));
    }
    days.put(monday.plusDays(2), totals("deltoideus-pars-acromialis", 5.3, "quadriceps-femoris", 2));
    days.put(monday.plusDays(4), totals("deltoideus-pars-acromialis", 5.0));

    List<UnusualSession> unusual = ProgrammingAlerts.unusualSessions(days, monday, monday.plusDays(6), MUSCLES);
    assertThat(unusual).singleElement().satisfies(u -> {
      assertThat(u.date()).isEqualTo(monday.plusDays(2));
      assertThat(u.region()).isEqualTo(MuscleRegion.SHOULDER);
      assertThat(u.base()).isEqualTo(4);
    });

    // A missing week before: no history, nothing said
    days.remove(monday.minusWeeks(3));
    assertThat(ProgrammingAlerts.unusualSessions(days, monday, monday.plusDays(6), MUSCLES)).isEmpty();
  }

  @Test
  @DisplayName("Unusual session: never on a region whose biggest day had fewer than 3 sets")
  void unusualSmallBase() {
    LocalDate monday = LocalDate.of(2026, 11, 16);
    Map<LocalDate, WeekTotals> days = new HashMap<>();
    for (int w = 1; w <= 4; w++) {
      days.put(monday.minusWeeks(w), totals("triceps-brachii", 2));
    }
    days.put(monday, totals("triceps-brachii", 6));
    assertThat(ProgrammingAlerts.unusualSessions(days, monday, monday.plusDays(6), MUSCLES)).isEmpty();
  }

  @Test
  @DisplayName("Three alerts at most, the furthest first")
  void top() {
    List<Alert> alerts =
        List.of(
            new Alert(Kind.GROUP_MINIMUM, "triceps-brachii", 3.8, 4, 4 / 3.8),
            new Alert(Kind.PATTERN_GAP, "SQUAT", 6, 12, 2),
            new Alert(Kind.PATTERN_GAP, "CORE", 8, 9, 9 / 8.0),
            new Alert(Kind.GROUP_MINIMUM, "deltoideus", 2, 4, 2.0001));
    assertThat(ProgrammingAlerts.top(alerts))
        .extracting(Alert::subject)
        .containsExactly("deltoideus", "SQUAT", "CORE");
  }
}
