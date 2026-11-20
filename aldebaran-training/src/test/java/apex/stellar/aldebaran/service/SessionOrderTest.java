package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.service.ProgrammingAlertsTest.MUSCLES;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.enums.HeavyDays;
import apex.stellar.aldebaran.model.enums.OrderMode;
import apex.stellar.aldebaran.service.SessionOrder.Day;
import apex.stellar.aldebaran.service.SessionOrder.Part;
import apex.stellar.aldebaran.service.SessionOrder.Result;
import apex.stellar.aldebaran.service.SessionOrder.Score;
import apex.stellar.aldebaran.service.SessionOrder.Week;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SessionOrder (lot D', D168-D171)")
class SessionOrderTest {

  private static final List<DayOfWeek> DAYS = List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY);

  /** A Skill that keeps every main group above its minimum, so only patterns decide. */
  private static WeekTotals skillVector() {
    return ProgrammingAlertsTest.totals(
        "quadriceps-femoris", 2, "biceps-femoris", 2, "gluteus-maximus", 2, "latissimus-dorsi", 2,
        "pectoralis-major-pars-clavicularis", 1, "pectoralis-major-pars-sternocostalis", 1,
        "deltoideus-pars-acromialis", 2, "triceps-brachii", 2);
  }

  /** The same themed programming every week: squat early in the week, pulling late, core on Saturday. */
  private static List<Week> themed(int weeks, Function<DayOfWeek, Boolean> strength, Set<DayOfWeek> locked) {
    List<Week> out = new ArrayList<>();
    for (int w = 0; w < weeks; w++) {
      List<Day> days = new ArrayList<>();
      for (DayOfWeek d : DAYS) {
        Part skill = new Part("s" + w + d, skillVector(), false, strength.apply(d), Set.of("quadriceps-femoris"));
        Part wod = new Part("w" + w + d, ProgrammingBalanceTest.day(d), false, false, Set.of());
        days.add(new Day(d, skill, wod, locked.contains(d)));
      }
      out.add(new Week(ProgrammingBalanceTest.START.plusWeeks(w), days));
    }
    return out;
  }

  @Test
  @DisplayName("Every order of n items, the identity first")
  void permutations() {
    assertThat(SessionOrder.permutations(3)).hasSize(6);
    assertThat(SessionOrder.permutations(3).getFirst()).containsExactly(0, 1, 2);
    assertThat(SessionOrder.permutations(6)).hasSize(720);
  }

  @Test
  @DisplayName("Fewer profiles in alert first, then excess, variety, moves")
  void scoreOrder() {
    Score fewerAlerts = new Score(1, 9, 5, 12);
    Score fewerMoves = new Score(2, 0, 0, 0);
    assertThat(Score.ORDER.compare(fewerAlerts, fewerMoves)).isNegative();
    assertThat(Score.ORDER.compare(new Score(0, 0, 1, 0), new Score(0, 0, 0, 6))).isPositive();
  }

  @Test
  @DisplayName("WOD only: moving the WODs inside each week removes profiles in alert, the Skills stay")
  void wodOnly() {
    List<Week> weeks = themed(4, d -> false, Set.of());

    Result result = SessionOrder.propose(weeks, MUSCLES, OrderMode.WOD_ONLY, HeavyDays.STRICT, null);

    assertThat(result.before().profilesInAlert()).isPositive();
    assertThat(result.after().profilesInAlert()).isLessThan(result.before().profilesInAlert());
    for (int w = 0; w < weeks.size(); w++) {
      List<Day> was = weeks.get(w).days();
      List<Day> now = result.weeks().get(w).days();
      for (int d = 0; d < was.size(); d++) {
        assertThat(now.get(d).skill()).isSameAs(was.get(d).skill());
      }
      // The same WODs, in another order
      assertThat(now.stream().map(Day::wod).toList()).containsExactlyInAnyOrderElementsOf(was.stream().map(Day::wod).toList());
    }
  }

  @Test
  @DisplayName("A locked day and a pinned part never move")
  void lockedAndPinned() {
    List<Week> weeks = themed(4, d -> false, Set.of(SATURDAY));
    Week first = weeks.getFirst();
    Day monday = first.days().getFirst();
    Part pinned = new Part("pinned", monday.wod().vector(), true, false, Set.of());
    List<Day> days = new ArrayList<>(first.days());
    days.set(0, new Day(MONDAY, monday.skill(), pinned, false));
    weeks.set(0, new Week(first.monday(), days));

    Result result = SessionOrder.propose(weeks, MUSCLES, OrderMode.WOD_ONLY, HeavyDays.STRICT, null);

    assertThat(result.weeks().getFirst().days().getFirst().wod()).isSameAs(pinned);
    for (int w = 0; w < weeks.size(); w++) {
      assertThat(result.weeks().get(w).days().getLast()).isEqualTo(weeks.get(w).days().getLast());
    }
  }

  @Test
  @DisplayName("Whole sessions, strict: never two strength Skills on consecutive days")
  void strictHeavyDays() {
    // Strength on Monday, Wednesday and Friday: any order must keep them apart
    List<Week> weeks = themed(4, d -> d == MONDAY || d == WEDNESDAY || d == FRIDAY, Set.of());

    Result result = SessionOrder.propose(weeks, MUSCLES, OrderMode.WHOLE_SESSIONS, HeavyDays.STRICT, null);

    for (Week week : result.weeks()) {
      List<Day> days = week.days();
      for (int d = 1; d < days.size(); d++) {
        boolean both = days.get(d - 1).skill().strength() && days.get(d).skill().strength();
        assertThat(both).as(week.monday() + " " + days.get(d).day()).isFalse();
      }
      // Whole sessions: each Skill still with its own WOD
      for (Day day : days) {
        assertThat(day.skill().key().substring(1)).isEqualTo(day.wod().key().substring(1));
      }
    }
    assertThat(Score.ORDER.compare(result.after(), result.before())).isNotPositive();
  }

  @Test
  @DisplayName("Strength days already in a row: the search still runs, never adding one more pair")
  void programmedHeavyDays() {
    // Strength Monday to Wednesday as programmed: two pairs in a row
    List<Week> weeks = themed(4, d -> d == MONDAY || d == TUESDAY || d == WEDNESDAY, Set.of());

    Result result = SessionOrder.propose(weeks, MUSCLES, OrderMode.WOD_AND_SKILLS, HeavyDays.STRICT, null);

    assertThat(result.after().profilesInAlert()).isLessThan(result.before().profilesInAlert());
    for (Week week : result.weeks()) {
      int pairs = 0;
      for (int d = 1; d < week.days().size(); d++) {
        pairs += week.days().get(d - 1).skill().strength() && week.days().get(d).skill().strength() ? 1 : 0;
      }
      assertThat(pairs).isLessThanOrEqualTo(2);
    }
  }

  @Test
  @DisplayName("Nothing better: the programming stays as it is")
  void nothingBetter() {
    List<Week> weeks = new ArrayList<>();
    for (int w = 0; w < 4; w++) {
      List<Day> days = new ArrayList<>();
      for (DayOfWeek d : DAYS) {
        WeekTotals same = ProgrammingAlertsTest.lines(skillVector(), apex.stellar.aldebaran.model.enums.MovementPattern.SQUAT, 3);
        days.add(new Day(d, null, new Part("w" + w + d, same, false, false, Set.of()), false));
      }
      weeks.add(new Week(LocalDate.of(2026, 11, 16).plusWeeks(w), days));
    }

    Result result = SessionOrder.propose(weeks, MUSCLES, OrderMode.WOD_ONLY, HeavyDays.STRICT, null);

    assertThat(result.after().moves()).isZero();
    assertThat(result.after()).isEqualTo(result.before());
  }
}
