package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementRecord;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.service.RecordInheritance.Effective;
import apex.stellar.aldebaran.service.RecordInheritance.Target;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordInheritanceTest {

  private final Movement clean = movement(1L, "Clean", Technique.MUSCLE, Technique.POWER, Technique.SQUAT, Technique.HANG);
  private final Movement jerk = movement(2L, "Jerk", Technique.POWER, Technique.SPLIT);
  private final Movement press = movement(3L, "Shoulder Press", Technique.PUSH);
  private final Movement cleanAndJerk = movement(4L, "Clean and Jerk", Technique.HANG);
  private final Movement s2o = movement(5L, "Shoulder-to-Overhead");
  private final Movement thruster = movement(6L, "Thruster");

  private static Movement movement(Long id, String name, Technique... techniques) {
    return Movement.builder()
        .id(id)
        .name(name)
        .equipment(new HashSet<>(Set.of(Equipment.BARBELL)))
        .techniques(new HashSet<>(Set.of(techniques)))
        .impliedEquipment(Equipment.BARBELL)
        .build();
  }

  private RecordInheritanceTest setUp() {
    cleanAndJerk.setComponents(
        new ArrayList<>(
            List.of(
                MovementComponent.builder().id(1L).chain(cleanAndJerk).component(clean).componentOrder(1).countsForRecords(true).build(),
                MovementComponent.builder().id(2L).chain(cleanAndJerk).component(jerk).componentOrder(2).countsForRecords(true).build())));
    thruster.setComponents(
        new ArrayList<>(
            List.of(MovementComponent.builder().id(3L).chain(thruster).component(press).componentOrder(1).countsForRecords(false).build())));
    s2o.getAcceptedMovements().addAll(Set.of(press, jerk));
    return this;
  }

  private static MovementRecord record(Movement m, String key, double kg, LocalDate date) {
    return MovementRecord.builder().movement(m).variantKey(key).reps(1).weightKg(kg).date(date).build();
  }

  private static final LocalDate DAY = LocalDate.of(2026, 1, 10);

  private Optional<Effective> find(List<Effective> list, Movement m, String key) {
    return list.stream().filter(e -> e.movement().equals(m) && e.variantKey().equals(key)).findFirst();
  }

  @Test
  @DisplayName("A record is stored precise and read at every variant it proves, with its source")
  void inherits_withinMovement() {
    setUp();
    List<Effective> read = RecordInheritance.effective(List.of(record(clean, ";MUSCLE", 80, DAY)), List.of());
    assertThat(find(read, clean, ";MUSCLE").orElseThrow().inherited()).isFalse();
    assertThat(find(read, clean, ";POWER").orElseThrow().inherited()).isTrue();
    assertThat(find(read, clean, "").orElseThrow().inherited()).isTrue();
    assertThat(find(read, clean, ";SQUAT")).isEmpty();
  }

  @Test
  @DisplayName("The heaviest wins; on a tie the variant's own record is kept")
  void best() {
    setUp();
    MovementRecord base = record(clean, "", 100, DAY);
    MovementRecord power = record(clean, ";POWER", 110, DAY);
    Effective read = find(RecordInheritance.effective(List.of(base, power), List.of()), clean, "").orElseThrow();
    assertThat(read.source()).isSameAs(power);

    MovementRecord tie = record(clean, ";POWER", 100, DAY.minusDays(30));
    Effective kept = find(RecordInheritance.effective(List.of(tie, base), List.of()), clean, "").orElseThrow();
    assertThat(kept.source()).isSameAs(base);
    assertThat(kept.inherited()).isFalse();
  }

  @Test
  @DisplayName("A chain counts for the components that accept it, per-component techniques included")
  void chain() {
    setUp();
    List<Effective> read =
        RecordInheritance.effective(List.of(record(cleanAndJerk, ";1:POWER,2:SPLIT", 120, DAY)), List.of());
    assertThat(find(read, clean, ";POWER")).isPresent();
    assertThat(find(read, clean, "")).isPresent();
    assertThat(find(read, jerk, ";SPLIT")).isPresent();
    assertThat(find(read, jerk, "")).isPresent();
    assertThat(find(read, cleanAndJerk, "")).isPresent();
  }

  @Test
  @DisplayName("A hang chain carries its start to the first component only")
  void chain_start() {
    setUp();
    Set<Target> reached = RecordInheritance.expand(cleanAndJerk, ";HANG", List.of());
    assertThat(reached).contains(new Target(clean, ";HANG")).doesNotContain(new Target(clean, ""));
    assertThat(reached).contains(new Target(jerk, ""));
  }

  @Test
  @DisplayName("A chain flagged as not counting gives nothing to its component")
  void chain_notCounting() {
    setUp();
    assertThat(RecordInheritance.effective(List.of(record(thruster, "", 100, DAY)), List.of()))
        .noneMatch(e -> e.movement().equals(press));
  }

  @Test
  @DisplayName("An either movement reads the best of what it accepts, never the reverse")
  void choice() {
    setUp();
    List<Effective> read =
        RecordInheritance.effective(
            List.of(record(press, ";PUSH", 70, DAY), record(jerk, ";SPLIT", 90, DAY)), List.of(s2o));
    assertThat(find(read, s2o, "").orElseThrow().source().getWeightKg()).isEqualTo(90);

    List<Effective> logged = RecordInheritance.effective(List.of(record(s2o, "", 100, DAY)), List.of(s2o));
    assertThat(logged).allMatch(e -> e.movement().equals(s2o));
  }

  @Test
  @DisplayName("Equipment is never crossed")
  void equipment() {
    setUp();
    List<Effective> read = RecordInheritance.effective(List.of(record(clean, "DUMBBELL;POWER", 40, DAY)), List.of());
    assertThat(find(read, clean, "DUMBBELL;")).isPresent();
    assertThat(find(read, clean, "")).isEmpty();
  }

  @Test
  @DisplayName("Sources: the movement, what it accepts and the chains that count for it")
  void sources() {
    setUp();
    assertThat(RecordInheritance.sources(clean, m -> m.equals(clean) ? List.of(cleanAndJerk) : List.of()))
        .containsExactlyInAnyOrder(clean, cleanAndJerk);
    assertThat(RecordInheritance.sources(s2o, m -> List.of())).containsExactlyInAnyOrder(s2o, press, jerk);
  }
}
