package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import apex.stellar.aldebaran.service.RandomAttendance.Choice;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RandomAttendance (lot D', D181)")
class RandomAttendanceTest {

  static double[] axes(double squat, double pull) {
    double[] v = new double[Axis.values().length];
    v[Axis.SQUAT.ordinal()] = squat;
    v[Axis.PULL.ordinal()] = pull;
    return v;
  }

  @Test
  @DisplayName("One week: the weight of the choices under the limit")
  void oneWeek() {
    List<List<Choice>> weeks =
        List.of(List.of(new Choice(0.25, axes(0, 10)), new Choice(0.75, axes(5, 5))));
    assertThat(RandomAttendance.missProbability(weeks, Axis.SQUAT, 30)).isCloseTo(0.25, within(1e-12));
    // A share exactly at the limit is not a miss
    assertThat(RandomAttendance.missProbability(weeks, Axis.SQUAT, 50)).isCloseTo(0.25, within(1e-12));
    assertThat(RandomAttendance.missProbability(weeks, Axis.SQUAT, 51)).isCloseTo(1, within(1e-12));
  }

  @Test
  @DisplayName("Matches a brute-force count over every combination of four weeks")
  void bruteForce() {
    Random random = new Random(42);
    List<List<Choice>> weeks = new ArrayList<>();
    for (int w = 0; w < 4; w++) {
      List<Choice> week = new ArrayList<>();
      int n = 3 + w;
      for (int c = 0; c < n; c++) {
        week.add(new Choice(1.0 / n, axes(random.nextInt(8), random.nextInt(8))));
      }
      weeks.add(week);
    }
    double expected = 0;
    for (Choice a : weeks.get(0)) {
      for (Choice b : weeks.get(1)) {
        for (Choice c : weeks.get(2)) {
          for (Choice d : weeks.get(3)) {
            double squat = a.axes()[0] + b.axes()[0] + c.axes()[0] + d.axes()[0];
            double total = squat + a.axes()[3] + b.axes()[3] + c.axes()[3] + d.axes()[3];
            if (total > 0 && squat / total * 100 < 40) {
              expected += a.weight() * b.weight() * c.weight() * d.weight();
            }
          }
        }
      }
    }
    assertThat(RandomAttendance.missProbability(weeks, Axis.SQUAT, 40)).isCloseTo(expected, within(1e-12));
  }

  @Test
  @DisplayName("The test programming of ProgrammingBalanceTest matches an independent exact count")
  void reference() {
    var plan = ProgrammingBalanceTest.plan(ProgrammingBalanceTest.START, 4, ProgrammingBalanceTest.cycle(4));
    var window = ProgrammingBalance.windows(plan).getFirst();
    // Expected values counted in fractions over every combination (Python, docs-private bench)
    assertThat(window.miss(List.of(3), Axis.SQUAT)).isCloseTo(0.02781875, within(1e-9));
    assertThat(window.miss(List.of(3), Axis.HINGE)).isCloseTo(0.1136, within(1e-9));
    assertThat(window.miss(List.of(3, 4, 5), Axis.CORE)).isCloseTo(0.11254000914494741, within(1e-9));
  }
}
