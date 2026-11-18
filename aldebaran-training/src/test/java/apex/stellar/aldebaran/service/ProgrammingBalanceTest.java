package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.service.ProgrammingAlertsTest.MUSCLES;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import apex.stellar.aldebaran.service.ProgrammingAlerts.Kind;
import apex.stellar.aldebaran.service.ProgrammingBalance.Plan;
import apex.stellar.aldebaran.service.ProgrammingBalance.ProfileBalance;
import apex.stellar.aldebaran.service.ProgrammingBalance.Window;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProgrammingBalance (lot D', D163, D164, D174-D181)")
class ProgrammingBalanceTest {

  static final LocalDate START = LocalDate.of(2026, 11, 16);

  /**
   * Squat spread over Monday to Wednesday, pulling on Thursday and Friday, core on Saturday only.
   * Full week: Squat 36.7 %, Pull 28.6 %, Hinge 12.2 %, Core 12.2 %, Push 10.2 %.
   */
  static WeekTotals day(DayOfWeek day) {
    WeekTotals t = new WeekTotals();
    return switch (day) {
      case MONDAY -> ProgrammingAlertsTest.lines(t, MovementPattern.SQUAT, 6, MovementPattern.PUSH_V, 2);
      case TUESDAY -> ProgrammingAlertsTest.lines(t, MovementPattern.SQUAT, 6, MovementPattern.PULL_V, 2);
      case WEDNESDAY -> ProgrammingAlertsTest.lines(t, MovementPattern.SQUAT, 6, MovementPattern.HINGE, 3);
      case THURSDAY -> ProgrammingAlertsTest.lines(t, MovementPattern.PULL_V, 6, MovementPattern.PUSH_H, 3);
      case FRIDAY -> ProgrammingAlertsTest.lines(t, MovementPattern.PULL_V, 6, MovementPattern.HINGE, 3);
      default -> ProgrammingAlertsTest.lines(t, MovementPattern.CORE_FLEXION, 6);
    };
  }

  /** {@code weeks} weeks of the same programming, Monday to Saturday, from {@code first}. */
  static Plan plan(LocalDate first, int weeks, List<LocalDate> cycle) {
    Map<LocalDate, Map<DayOfWeek, WeekTotals>> days = new HashMap<>();
    for (int w = 0; w < weeks; w++) {
      Map<DayOfWeek, WeekTotals> week = new EnumMap<>(DayOfWeek.class);
      for (DayOfWeek d : List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY)) {
        week.put(d, day(d));
      }
      days.put(first.plusWeeks(w), week);
    }
    return new Plan(days, cycle, MUSCLES);
  }

  static List<LocalDate> cycle(int weeks) {
    List<LocalDate> c = new ArrayList<>();
    for (int w = 0; w < weeks; w++) {
      c.add(START.plusWeeks(w));
    }
    return c;
  }

  @Test
  @DisplayName("Profiles: every combination of the training days, none as large as the full week")
  void profiles() {
    var days = List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY);
    assertThat(ProgrammingBalance.profiles(days, 3)).hasSize(20).first().isEqualTo(EnumSet.of(MONDAY, TUESDAY, WEDNESDAY));
    assertThat(ProgrammingBalance.profiles(days, 4)).hasSize(15);
    assertThat(ProgrammingBalance.profiles(days, 5)).hasSize(6);
    assertThat(ProgrammingBalance.profiles(days.subList(0, 5), 5)).isEmpty();
  }

  @Test
  @DisplayName("Training days: days with a counted session in the cycle")
  void trainingDays() {
    Plan plan = plan(START, 4, cycle(4));
    plan.days().get(START).remove(SATURDAY);
    assertThat(plan.trainingDays()).containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY);
    plan.days().values().forEach(w -> w.remove(SATURDAY));
    assertThat(plan.trainingDays()).doesNotContain(SATURDAY);
    assertThat(plan(START, 1, cycle(1)).byDate()).containsKey(START.plusDays(5)).hasSize(6);
  }

  @Test
  @DisplayName("Regularity: an axis spread over several days is judged, one on a single day is not (D181)")
  void regularity() {
    Plan plan = plan(START, 4, cycle(4));
    Window window = ProgrammingBalance.windows(plan).getFirst();
    assertThat(window.fullAxes().get(Axis.SQUAT)).isBetween(36.7, 36.8);
    // Random 3-day athletes miss Squat 2.8 % of the time, Pull 5.9 %, Hinge 11.4 %, Core 31 %
    assertThat(window.judged(3)).containsExactlyInAnyOrder(Axis.SQUAT, Axis.PULL);
    assertThat(window.judged(4)).contains(Axis.SQUAT, Axis.PULL, Axis.HINGE, Axis.PUSH).doesNotContain(Axis.CORE);
    // An athlete without fixed days misses Core 11.3 % of the time: it sits on one day
    assertThat(window.concentrated()).containsExactly(Axis.CORE);
  }

  @Test
  @DisplayName("The full week is never in pattern gap; a profile without the squat days is")
  void profileAlert() {
    Plan plan = plan(START, 4, cycle(4));
    List<Window> windows = ProgrammingBalance.windows(plan);
    ProfileBalance full = ProgrammingBalance.evaluate(plan, windows, plan.trainingDays());
    assertThat(full.isFull()).isTrue();
    assertThat(full.inAlert()).isFalse();

    ProfileBalance noSquat = ProgrammingBalance.evaluate(plan, windows, EnumSet.of(THURSDAY, FRIDAY, SATURDAY));
    assertThat(noSquat.inAlert()).isTrue();
    assertThat(noSquat.excess()).isPositive();
    assertThat(noSquat.alerts()).singleElement().satisfies(a -> {
      assertThat(a.kind()).isEqualTo(Kind.PATTERN_GAP);
      assertThat(a.subject()).isEqualTo("SQUAT");
    });

    // No core at all, but Core sits on Saturday only: not judged, no alert
    ProfileBalance noCore = ProgrammingBalance.evaluate(plan, windows, EnumSet.of(MONDAY, WEDNESDAY, FRIDAY));
    assertThat(noCore.inAlert()).isFalse();
    assertThat(noCore.days()).containsExactly(MONDAY, WEDNESDAY, FRIDAY);
  }

  @Test
  @DisplayName("Windows slide over the cycle; the worst one is shown, the last one when all are fine")
  void windows() {
    Plan plan = plan(START, 5, cycle(5));
    // Week 5 adds squats on Thursday: the second window is less short of squats
    plan.days().get(START.plusWeeks(4)).put(THURSDAY, ProgrammingAlertsTest.lines(new WeekTotals(), MovementPattern.SQUAT, 6));
    List<Window> windows = ProgrammingBalance.windows(plan);
    assertThat(windows).hasSize(2);
    assertThat(ProgrammingBalance.evaluate(plan, windows, EnumSet.of(THURSDAY, FRIDAY, SATURDAY)).window().start())
        .isEqualTo(START);
    assertThat(ProgrammingBalance.evaluate(plan, windows, EnumSet.of(MONDAY, TUESDAY, THURSDAY, FRIDAY)).window().start())
        .isEqualTo(START.plusWeeks(1));
  }

  @Test
  @DisplayName("Group minimum applies to a profile with muscles, after 3 trained weeks")
  void groupMinimum() {
    Plan plan = plan(START, 4, cycle(4));
    plan.days().values().forEach(week -> week.put(MONDAY, ProgrammingAlertsTest.totals("quadriceps-femoris", 6)));
    List<Window> windows = ProgrammingBalance.windows(plan);
    ProfileBalance monday = ProgrammingBalance.evaluate(plan, windows, EnumSet.of(MONDAY, TUESDAY, WEDNESDAY));
    assertThat(monday.trainedWeeks()).isEqualTo(4);
    assertThat(monday.alerts()).hasSize(3).allMatch(a -> a.kind() == Kind.GROUP_MINIMUM);
  }
}
