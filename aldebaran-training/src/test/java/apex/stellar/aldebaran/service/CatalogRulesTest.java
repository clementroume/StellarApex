package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import apex.stellar.aldebaran.dto.WodMovementDisplay;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.InvalidWodMovementException;
import apex.stellar.aldebaran.model.entities.ComponentTechnique;
import apex.stellar.aldebaran.model.entities.DumbbellBase;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementJointAction;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment.TriggerKind;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.JointAction;
import apex.stellar.aldebaran.model.enums.JointAction.Regime;
import apex.stellar.aldebaran.model.enums.RequiredRule;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.service.CatalogRules.Spec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogRulesTest {

  private static Spec spec(Set<RequiredRule> rules, Set<DumbbellBase> bases) {
    return new Spec(
        "Move",
        Set.of(Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL),
        Set.of(Technique.values()),
        Equipment.BARBELL,
        false,
        rules,
        bases);
  }

  private static WodMovement line(Set<String> equipment, Set<String> techniques) {
    return WodMovement.builder()
        .equipment(new HashSet<>(equipment))
        .techniques(new HashSet<>(techniques))
        .build();
  }

  @Test
  @DisplayName("A value equal to the base is removed: one written form per performance")
  void normalize_removesBaseValue() {
    Spec two = spec(Set.of(), Set.of(new DumbbellBase("", 2)));
    WodMovement dumbbells = line(Set.of("DUMBBELL"), Set.of("DOUBLE"));
    CatalogRules.normalize(two, dumbbells);
    assertThat(dumbbells.getTechniques()).isEmpty();

    WodMovement oneArm = line(Set.of("DUMBBELL"), Set.of("ONE_ARM"));
    CatalogRules.normalize(two, oneArm);
    assertThat(oneArm.getTechniques()).containsExactly("ONE_ARM");

    WodMovement kettlebell = line(Set.of("KETTLEBELL"), Set.of("ONE_ARM"));
    CatalogRules.normalize(two, kettlebell);
    assertThat(kettlebell.getTechniques()).isEmpty();
  }

  @Test
  @DisplayName("The base count follows the load position; a swing keeps ONE_ARM")
  void baseCount_perPosition() {
    Spec lunge = spec(Set.of(), Set.of(new DumbbellBase("OVERHEAD", 1), new DumbbellBase("FRONT_RACK", 2), new DumbbellBase("", null)));
    assertThat(CatalogRules.baseCount(lunge, Set.of("DUMBBELL"), Set.of("OVERHEAD"))).isEqualTo(1);
    assertThat(CatalogRules.baseCount(lunge, Set.of("DUMBBELL"), Set.of("FRONT_RACK"))).isEqualTo(2);
    assertThat(CatalogRules.baseCount(lunge, Set.of("DUMBBELL"), Set.of())).isNull();
    assertThat(CatalogRules.implementCount(lunge, Set.of("DUMBBELL"), Set.of("OVERHEAD", "DOUBLE"))).isEqualTo(2);

    Spec swing = spec(Set.of(RequiredRule.SWING_STYLE), Set.of());
    WodMovement line = line(Set.of("KETTLEBELL"), Set.of("ONE_ARM", "RUSSIAN"));
    CatalogRules.normalize(swing, line);
    assertThat(line.getTechniques()).contains("ONE_ARM");
  }

  @Test
  @DisplayName("High or low bar is a back rack")
  void normalize_barPosition() {
    WodMovement squat = line(Set.of(), Set.of("LOW_BAR"));
    CatalogRules.normalize(spec(Set.of(), Set.of()), squat);
    assertThat(squat.getTechniques()).containsExactlyInAnyOrder("LOW_BAR", "BACK_RACK");
  }

  @Test
  @DisplayName("Two values of an exclusive type are refused; a bar position is not one")
  void exclusive() {
    Spec spec = spec(Set.of(), Set.of());
    assertThatThrownBy(
            () -> CatalogRules.normalizeAndCheck(spec, line(Set.of(), Set.of("POWER", "SPLIT")), List.of()))
        .isInstanceOf(InvalidWodMovementException.class);
    CatalogRules.normalizeAndCheck(spec, line(Set.of(), Set.of("BACK_RACK", "HIGH_BAR")), List.of());
    CatalogRules.normalizeAndCheck(spec, line(Set.of(), Set.of("PAUSED", "TEMPO")), List.of());
    CatalogRules.normalizeAndCheck(spec, line(Set.of(), Set.of("DEFICIT", "FREESTANDING")), List.of());
    assertThatThrownBy(
            () -> CatalogRules.normalizeAndCheck(spec, line(Set.of(), Set.of("RUSSIAN", "AMERICAN")), List.of()))
        .isInstanceOf(InvalidWodMovementException.class);
    assertThatThrownBy(
            () -> CatalogRules.normalizeAndCheck(spec, line(Set.of(), Set.of("HIGH_BAR", "FRONT_RACK")), List.of()))
        .isInstanceOf(InvalidWodMovementException.class);
  }

  @Test
  @DisplayName("Required values, rule by rule")
  void required() {
    assertRefused(RequiredRule.AMPLITUDE, Set.of(), Set.of());
    assertAccepted(RequiredRule.AMPLITUDE, Set.of(), Set.of("TOES_TO_BAR"));
    assertRefused(RequiredRule.ROTATIONS, Set.of(), Set.of());
    assertAccepted(RequiredRule.ROTATIONS, Set.of(), Set.of("DOUBLE_UNDER"));
    assertRefused(RequiredRule.SWING_STYLE, Set.of(), Set.of());
    assertAccepted(RequiredRule.SWING_STYLE, Set.of(), Set.of("AMERICAN"));
    assertRefused(RequiredRule.EQUIPMENT, Set.of(), Set.of());
    assertAccepted(RequiredRule.EQUIPMENT, Set.of("BARBELL"), Set.of());
    assertAccepted(RequiredRule.LOAD_POSITION_IF_EQUIPMENT, Set.of(), Set.of());
    assertRefused(RequiredRule.LOAD_POSITION_IF_EQUIPMENT, Set.of("BARBELL"), Set.of());
    assertAccepted(RequiredRule.LOAD_POSITION_IF_EQUIPMENT, Set.of("BARBELL"), Set.of("FRONT_RACK"));
  }

  private static void assertRefused(RequiredRule rule, Set<String> equipment, Set<String> techniques) {
    assertThatThrownBy(
            () -> CatalogRules.normalizeAndCheck(spec(Set.of(rule), Set.of()), line(equipment, techniques), List.of()))
        .isInstanceOf(InvalidWodMovementException.class);
  }

  private static void assertAccepted(RequiredRule rule, Set<String> equipment, Set<String> techniques) {
    CatalogRules.normalizeAndCheck(spec(Set.of(rule), Set.of()), line(equipment, techniques), List.of());
  }

  @Test
  @DisplayName("A component technique must belong to that component")
  void components() {
    WodMovement good = line(Set.of(), Set.of());
    good.setComponentTechniques(Set.of(new ComponentTechnique(2, "SPLIT")));
    CatalogRules.normalizeAndCheck(spec(Set.of(), Set.of()), good, List.of(Set.of(Technique.POWER), Set.of(Technique.SPLIT)));

    WodMovement wrong = line(Set.of(), Set.of());
    wrong.setComponentTechniques(Set.of(new ComponentTechnique(1, "SPLIT")));
    assertThatThrownBy(
            () -> CatalogRules.normalizeAndCheck(spec(Set.of(), Set.of()), wrong, List.of(Set.of(Technique.POWER), Set.of(Technique.SPLIT))))
        .isInstanceOf(InvalidWodMovementException.class);
  }

  private static Movement movement(Long id, String name) {
    return Movement.builder()
        .id(id)
        .name(name)
        .equipment(new HashSet<>(Set.of(Equipment.BARBELL, Equipment.DUMBBELL)))
        .techniques(new HashSet<>(Set.of(Technique.POWER, Technique.HANG, Technique.PAUSED)))
        .impliedEquipment(Equipment.BARBELL)
        .build();
  }

  private static MovementUsualName usual(Movement m, String name, Set<String> techniques, Set<String> equipment) {
    return MovementUsualName.builder()
        .movement(m)
        .nameEn(name)
        .techniques(new HashSet<>(techniques))
        .equipment(new HashSet<>(equipment))
        .build();
  }

  @Test
  @DisplayName("A line reads as its most precise usual name, then what remains")
  void display() {
    Movement clean = movement(1L, "Clean");
    clean.getUsualNames().add(usual(clean, "Clean", Set.of(), Set.of()));
    clean.getUsualNames().add(usual(clean, "Power Clean", Set.of("POWER"), Set.of()));
    clean.getUsualNames().add(usual(clean, "Hang Power Clean", Set.of("HANG", "POWER"), Set.of()));

    WodMovement line = line(Set.of("BARBELL"), Set.of("POWER", "PAUSED"));
    line.setMovement(clean);
    WodMovementDisplay display = CatalogRules.display(line);
    assertThat(display.usualNameEn()).isEqualTo("Power Clean");
    assertThat(display.remainingTechniques()).containsExactly("PAUSED");
    assertThat(display.remainingEquipment()).isEmpty();
    assertThat(display.variant()).isTrue();

    WodMovement base = line(Set.of(), Set.of());
    base.setMovement(clean);
    assertThat(CatalogRules.display(base).variant()).isFalse();

    WodMovement implied = line(Set.of("BARBELL"), Set.of());
    implied.setMovement(clean);
    assertThat(CatalogRules.display(implied).variant()).isFalse();
  }

  @Test
  @DisplayName("Adjustments change the role; on a collision the lowest role wins")
  void effectiveRoles_lowestWins() {
    Movement m = movement(1L, "Move");
    Muscle glutes = Muscle.builder().id(1L).medicalName("Glutes").build();
    m.getTargetedMuscles().add(MovementMuscle.builder().muscle(glutes).role(MuscleRole.AGONIST).build());
    m.getRoleAdjustments().add(adjustment(m, "POWER", glutes, MuscleRole.SYNERGIST));
    m.getRoleAdjustments().add(adjustment(m, "HANG", glutes, MuscleRole.STABILIZER));

    assertThat(CatalogRules.effectiveRoles(m, Set.of(), Set.of())).containsEntry(glutes, MuscleRole.AGONIST);
    assertThat(CatalogRules.effectiveRoles(m, Set.of("POWER"), Set.of())).containsEntry(glutes, MuscleRole.SYNERGIST);
    Map<Muscle, MuscleRole> both = CatalogRules.effectiveRoles(m, Set.of("POWER", "HANG"), Set.of());
    assertThat(both).containsEntry(glutes, MuscleRole.STABILIZER);
  }

  private static MovementRoleAdjustment adjustment(Movement m, String trigger, Muscle muscle, MuscleRole role) {
    return MovementRoleAdjustment.builder()
        .id((long) trigger.hashCode())
        .movement(m)
        .triggerKind(TriggerKind.TECHNIQUE)
        .trigger(trigger)
        .muscle(muscle)
        .role(role)
        .build();
  }

  @Test
  @DisplayName("A chain cannot contain itself, even through another movement")
  void checkMovement_cycle() {
    Movement a = movement(1L, "A");
    Movement b = movement(2L, "B");
    a.setComponents(new ArrayList<>(List.of(MovementComponent.builder().chain(a).component(b).componentOrder(1).build())));
    b.getAcceptedMovements().add(a);
    assertThatThrownBy(() -> CatalogRules.checkMovement(a)).isInstanceOf(DataConflictException.class);
  }

  @Test
  @DisplayName("A movement never lists a muscle and its group")
  void checkMovement_muscleAndGroup() {
    Movement m = movement(1L, "Move");
    Muscle group = Muscle.builder().id(1L).medicalName("Deltoids").build();
    Muscle child = Muscle.builder().id(2L).medicalName("Anterior Deltoid").parent(group).build();
    m.getTargetedMuscles().add(MovementMuscle.builder().id(1L).muscle(group).role(MuscleRole.AGONIST).build());
    m.getTargetedMuscles().add(MovementMuscle.builder().id(2L).muscle(child).role(MuscleRole.SYNERGIST).build());
    assertThatThrownBy(() -> CatalogRules.checkMovement(m)).isInstanceOf(DataConflictException.class);
  }

  @Test
  @DisplayName("An adjustment or a usual name only uses values the movement allows")
  void checkMovement_values() {
    Movement m = movement(1L, "Move");
    Muscle muscle = Muscle.builder().id(1L).medicalName("Glutes").build();
    m.getRoleAdjustments().add(adjustment(m, "SPLIT", muscle, MuscleRole.AGONIST));
    assertThatThrownBy(() -> CatalogRules.checkMovement(m)).isInstanceOf(DataConflictException.class);

    Movement n = movement(2L, "Other");
    n.getUsualNames().add(usual(n, "Split Other", Set.of("SPLIT"), Set.of()));
    assertThatThrownBy(() -> CatalogRules.checkMovement(n)).isInstanceOf(DataConflictException.class);
  }

  @Test
  @DisplayName("An agonist must be able to act on a joint of the movement; a stabilizer need not")
  void checkMovement_joints() {
    Movement m = movement(1L, "Move");
    m.getJointActions().add(new MovementJointAction("", JointAction.KNEE_EXT, Regime.DYNAMIC));
    Muscle glutes = Muscle.builder().id(1L).medicalName("Glutes").jointActions(Set.of(JointAction.HIP_EXT)).build();
    m.getTargetedMuscles().add(MovementMuscle.builder().id(1L).muscle(glutes).role(MuscleRole.STABILIZER).build());
    CatalogRules.checkMovement(m);

    m.getTargetedMuscles().clear();
    m.getTargetedMuscles().add(MovementMuscle.builder().id(2L).muscle(glutes).role(MuscleRole.AGONIST).build());
    assertThatThrownBy(() -> CatalogRules.checkMovement(m)).isInstanceOf(DataConflictException.class);

    m.getJointActions().add(new MovementJointAction("", JointAction.HIP_EXT, Regime.DYNAMIC));
    CatalogRules.checkMovement(m);
  }
}
