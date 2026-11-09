package apex.stellar.aldebaran.service;

import static apex.stellar.aldebaran.model.enums.Technique.*;
import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.enums.Technique;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VariantDominanceTest {

  /** The whole rule, as validated in the catalog review: everything else is a separate record. */
  private static final Map<Technique, Set<Technique>> EXPECTED_CLOSURE =
      Map.of(
          MUSCLE, Set.of(POWER),
          STRICT, Set.of(KIPPING, BUTTERFLY),
          TOES_TO_BAR, Set.of(KNEES_TO_ELBOWS, KNEE_RAISE),
          KNEES_TO_ELBOWS, Set.of(KNEE_RAISE));

  private static final Set<Technique> EXPECTED_ABSENT =
      EnumSet.of(
          MUSCLE, POWER, SQUAT, SPLIT, DEFICIT, PAUSED, TEMPO, TOUCH_AND_GO, DEAD_STOP, STRICT,
          KIPPING, BUTTERFLY, HAND_RELEASE, LEGLESS, CONVENTIONAL, SUMO, CLOSE_STANCE, HIGH_BAR,
          LOW_BAR, ALTERNATING, CHEST_TO_BAR, CHEST_TO_WALL, FREESTANDING);

  @ParameterizedTest
  @EnumSource(Technique.class)
  @DisplayName("Every technique: exactly the expected values")
  void closure_isExhaustive(Technique technique) {
    assertThat(VariantDominance.closure(technique))
        .isEqualTo(EXPECTED_CLOSURE.getOrDefault(technique, Set.of()));
  }

  @ParameterizedTest
  @EnumSource(Technique.class)
  @DisplayName("Every technique: droppable only when the rule says so")
  void countsForAbsent_isExhaustive(Technique technique) {
    assertThat(VariantDominance.countsForAbsent(technique))
        .isEqualTo(EXPECTED_ABSENT.contains(technique));
  }

  @Test
  @DisplayName("Reception: muscle proves power proves any style, squat and split prove any style")
  void reception() {
    assertThat(VariantDominance.countsFor(List.of(MUSCLE), List.of(POWER))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(MUSCLE), List.of())).isTrue();
    assertThat(VariantDominance.countsFor(List.of(POWER), List.of())).isTrue();
    assertThat(VariantDominance.countsFor(List.of(SQUAT), List.of())).isTrue();
    assertThat(VariantDominance.countsFor(List.of(POWER), List.of(MUSCLE))).isFalse();
    assertThat(VariantDominance.countsFor(List.of(), List.of(POWER))).isFalse();
    assertThat(VariantDominance.countsFor(List.of(SQUAT), List.of(POWER))).isFalse();
  }

  @Test
  @DisplayName("Press: the absence of style proves the push press, never the reverse")
  void pushPress() {
    assertThat(VariantDominance.countsFor(List.of(), List.of(PUSH))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(PAUSED), List.of(PUSH))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(PUSH), List.of())).isFalse();
  }

  @Test
  @DisplayName("Style: strict proves kipping and butterfly, not the reverse")
  void style() {
    assertThat(VariantDominance.countsFor(List.of(STRICT), List.of(KIPPING))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(STRICT), List.of(BUTTERFLY))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(STRICT), List.of())).isTrue();
    assertThat(VariantDominance.countsFor(List.of(KIPPING), List.of(STRICT))).isFalse();
  }

  @Test
  @DisplayName("Amplitude: toes-to-bar proves knees-to-elbows and knee raise, never nothing")
  void amplitude() {
    assertThat(VariantDominance.countsFor(List.of(TOES_TO_BAR), List.of(KNEES_TO_ELBOWS))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(TOES_TO_BAR), List.of(KNEE_RAISE))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(KNEE_RAISE), List.of(TOES_TO_BAR))).isFalse();
    assertThat(VariantDominance.countsFor(List.of(TOES_TO_BAR), List.of())).isFalse();
  }

  @Test
  @DisplayName("Separate records: a value without relation is never dropped")
  void separate() {
    assertThat(VariantDominance.countsFor(List.of(HANG), List.of())).isFalse();
    assertThat(VariantDominance.countsFor(List.of(FRONT_RACK), List.of())).isFalse();
    assertThat(VariantDominance.countsFor(List.of(RUSSIAN), List.of(AMERICAN))).isFalse();
    assertThat(VariantDominance.countsFor(List.of(HALF), List.of())).isFalse();
  }

  @Test
  @DisplayName("Combinations: each value is judged on its own")
  void combinations() {
    assertThat(VariantDominance.countsFor(List.of(HANG, POWER), List.of(HANG))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(HANG, POWER), List.of())).isFalse();
    assertThat(VariantDominance.countsFor(List.of(BACK_RACK, HIGH_BAR), List.of(BACK_RACK))).isTrue();
    assertThat(VariantDominance.countsFor(List.of(STRICT, CHEST_TO_BAR), List.of(KIPPING))).isTrue();
  }

  @Test
  @DisplayName("Targets: every variant proved and allowed, and only those")
  void targets() {
    Set<Technique> allowed = EnumSet.of(HANG, MUSCLE, POWER, SQUAT, SPLIT);
    assertThat(VariantDominance.targets(List.of(HANG, MUSCLE), allowed))
        .containsExactlyInAnyOrder(
            Set.of(HANG, MUSCLE), Set.of(HANG, POWER), Set.of(HANG));
    assertThat(VariantDominance.targets(List.of(), EnumSet.of(PUSH, PAUSED)))
        .containsExactlyInAnyOrder(Set.of(), Set.of(PUSH));
    assertThat(VariantDominance.targets(List.of(PUSH), EnumSet.of(PUSH))).containsExactly(Set.of(PUSH));
  }

  @ParameterizedTest
  @EnumSource(Technique.class)
  @DisplayName("Targets agree with countsFor for every technique")
  void targets_agreeWithCountsFor(Technique technique) {
    Set<Technique> all = EnumSet.allOf(Technique.class);
    for (Set<Technique> target : VariantDominance.targets(List.of(technique), all)) {
      assertThat(VariantDominance.countsFor(List.of(technique), target))
          .as("%s -> %s", technique, target)
          .isTrue();
    }
  }
}
