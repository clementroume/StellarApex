package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.MovementResponse;
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
import apex.stellar.aldebaran.model.enums.RequiredRule;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Technique.TechniqueCategory;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Rules of the movement catalog, written once: how a WOD line is normalized and checked, how it
 * reads, which roles it gives, and what a catalog movement must respect.
 */
public final class CatalogRules {

  private static final String ONE_ARM = Technique.ONE_ARM.name();
  private static final String DOUBLE = Technique.DOUBLE.name();
  private static final String BACK_RACK = Technique.BACK_RACK.name();

  /** Precise a back squat: written with {@code BACK_RACK}, not a second position. */
  private static final Set<String> BAR_POSITIONS =
      Set.of(Technique.HIGH_BAR.name(), Technique.LOW_BAR.name());

  private CatalogRules() {}

  /**
   * What the line rules need to know about a movement.
   *
   * @param name Movement name.
   * @param equipment Allowed equipment.
   * @param techniques Allowed techniques.
   * @param impliedEquipment Equipment understood when a line writes none.
   * @param impliedRequiresPosition The implied equipment needs a load position.
   * @param requiredRules What a line must state.
   * @param dumbbellBases Number of dumbbells without a laterality.
   */
  public record Spec(
      String name,
      Set<Equipment> equipment,
      Set<Technique> techniques,
      @Nullable Equipment impliedEquipment,
      boolean impliedRequiresPosition,
      Set<RequiredRule> requiredRules,
      Set<DumbbellBase> dumbbellBases) {

    /**
     * Spec of a catalog entity.
     *
     * @param m The movement.
     * @return Its spec.
     */
    public static Spec of(Movement m) {
      return new Spec(
          m.getName(),
          m.getEquipment(),
          m.getTechniques(),
          m.getImpliedEquipment(),
          m.isImpliedRequiresPosition(),
          m.getRequiredRules(),
          m.getDumbbellBases());
    }

    /**
     * Spec of a cached catalog response.
     *
     * @param m The movement.
     * @return Its spec.
     */
    public static Spec of(MovementResponse m) {
      return new Spec(
          m.name(),
          m.equipment(),
          m.techniques(),
          m.impliedEquipment(),
          m.impliedRequiresPosition(),
          m.requiredRules() == null ? Set.of() : m.requiredRules(),
          m.dumbbellBases() == null ? Set.of() : m.dumbbellBases());
    }
  }

  // -------------------------------------------------------------------------
  // WOD lines
  // -------------------------------------------------------------------------

  /**
   * Writes a line in its single canonical form, then checks it: a value equal to the base is
   * removed (one written form per performance), then allowed values, exclusive types, required
   * values and component techniques are checked.
   *
   * @param spec The line's movement.
   * @param line The line.
   * @param components Allowed techniques of each component of a chain, in order.
   * @throws InvalidWodMovementException on the first rule broken.
   */
  public static void normalizeAndCheck(
      Spec spec, WodMovement line, List<Set<Technique>> components) {
    normalize(spec, line);
    line.checkVariantsAllowed(spec.name(), spec.equipment(), spec.techniques());
    checkExclusive(spec.name(), line.getTechniques());
    checkRequired(spec, line.getEquipment(), line.getTechniques());
    checkComponents(spec.name(), line.getComponentTechniques(), components);
  }

  /**
   * Canonical writing of a line.
   *
   * @param spec The line's movement.
   * @param line The line, modified in place.
   */
  public static void normalize(Spec spec, WodMovement line) {
    Set<String> techniques = line.getTechniques();
    if (techniques.stream().anyMatch(BAR_POSITIONS::contains)) {
      techniques.add(BACK_RACK);
    }
    Integer base = baseCount(spec, line.getEquipment(), techniques);
    if (base == null) {
      return;
    }
    String redundant = base == 1 ? ONE_ARM : DOUBLE;
    if (techniques.contains(redundant)) {
      techniques.remove(redundant);
    }
  }

  /**
   * Number of implements of a line that writes no laterality.
   *
   * @param spec The movement.
   * @param equipment The line's equipment.
   * @param techniques The line's techniques.
   * @return 1 or 2, or null when not applicable or free.
   */
  public static @Nullable Integer baseCount(
      Spec spec, Collection<String> equipment, Collection<String> techniques) {
    // A swing holds one kettlebell with both hands: ONE_ARM is a real variant there
    if (spec.requiredRules().contains(RequiredRule.SWING_STYLE)) {
      return null;
    }
    if (equipment.contains(Equipment.DUMBBELL.name())) {
      return spec.dumbbellBases().stream()
          .filter(b -> !b.loadPosition().isEmpty() && techniques.contains(b.loadPosition()))
          .findFirst()
          .or(
              () ->
                  spec.dumbbellBases().stream().filter(b -> b.loadPosition().isEmpty()).findFirst())
          .map(DumbbellBase::implementCount)
          .orElse(null);
    }
    return equipment.contains(Equipment.KETTLEBELL.name()) ? Integer.valueOf(1) : null;
  }

  /**
   * Number of implements actually used on a line.
   *
   * @param spec The movement.
   * @param equipment The line's equipment.
   * @param techniques The line's techniques.
   * @return 1 or 2, or null when not applicable or free.
   */
  public static @Nullable Integer implementCount(
      Spec spec, Collection<String> equipment, Collection<String> techniques) {
    if (!equipment.contains(Equipment.DUMBBELL.name())
        && !equipment.contains(Equipment.KETTLEBELL.name())) {
      return null;
    }
    if (techniques.contains(DOUBLE)) {
      return 2;
    }
    if (techniques.contains(ONE_ARM)) {
      return 1;
    }
    return baseCount(spec, equipment, techniques);
  }

  private static void checkExclusive(String movementName, Collection<String> techniques) {
    for (Set<Technique> set : Technique.exclusiveSets()) {
      List<String> found =
          set.stream().map(Enum::name).filter(techniques::contains).toList();
      if (found.size() > 1) {
        throw new InvalidWodMovementException(
            "error.wod.movement.technique.exclusive", found.getLast(), movementName);
      }
    }
  }

  private static void checkRequired(
      Spec spec, Collection<String> equipment, Collection<String> techniques) {
    for (RequiredRule rule : spec.requiredRules()) {
      boolean satisfied =
          switch (rule) {
            case AMPLITUDE -> hasCategory(techniques, TechniqueCategory.AMPLITUDE);
            case ROTATIONS -> hasCategory(techniques, TechniqueCategory.ROTATIONS);
            case SWING_STYLE ->
                techniques.contains(Technique.RUSSIAN.name())
                    || techniques.contains(Technique.AMERICAN.name());
            case EQUIPMENT -> !equipment.isEmpty();
            case LOAD_POSITION_IF_EQUIPMENT ->
                equipment.isEmpty() || hasCategory(techniques, TechniqueCategory.LOAD_POSITION);
          };
      if (!satisfied) {
        throw new InvalidWodMovementException(
            "error.wod.movement.required." + rule.name().toLowerCase(), spec.name());
      }
    }
  }

  private static boolean hasCategory(Collection<String> techniques, TechniqueCategory category) {
    return techniques.stream().anyMatch(t -> Technique.valueOf(t).getCategory() == category);
  }

  private static void checkComponents(
      String movementName, Set<ComponentTechnique> values, List<Set<Technique>> components) {
    for (ComponentTechnique value : values) {
      int order = value.componentOrder();
      boolean valid =
          order >= 1
              && order <= components.size()
              && components.get(order - 1).stream()
                  .anyMatch(t -> t.name().equals(value.technique()));
      if (!valid) {
        throw new InvalidWodMovementException(
            "error.wod.movement.technique.invalid", value.technique(), movementName);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Reading a line
  // -------------------------------------------------------------------------

  /**
   * How a line reads.
   *
   * @param line The line, with its movement.
   * @return Its display.
   */
  public static @Nullable WodMovementDisplay display(WodMovement line) {
    Movement movement = line.getMovement();
    if (movement == null) {
      return null;
    }
    Spec spec = Spec.of(movement);
    Set<String> equipment = visibleEquipment(spec, line.getEquipment(), line.getTechniques());
    Set<String> techniques = new LinkedHashSet<>(new TreeSet<>(line.getTechniques()));
    line.getComponentTechniques()
        .forEach(c -> techniques.add(c.componentOrder() + ":" + c.technique()));
    Optional<MovementUsualName> usual = usualName(movement, equipment, techniques);
    Set<String> remainingEquipment = new LinkedHashSet<>(equipment);
    Set<String> remainingTechniques = new LinkedHashSet<>(techniques);
    usual.ifPresent(
        u -> {
          remainingEquipment.removeAll(u.getEquipment());
          remainingTechniques.removeAll(u.getTechniques());
        });
    String percentBase = percentBaseKey(spec, line);
    return new WodMovementDisplay(
        usual.map(MovementUsualName::getNameEn).orElse(null),
        usual.map(MovementUsualName::getNameFr).orElse(null),
        remainingEquipment,
        remainingTechniques,
        implementCount(spec, line.getEquipment(), line.getTechniques()),
        !LiftVariant.compose(equipment, techniques).equals(percentBase),
        percentBase);
  }

  /**
   * The variant a percentage of RM refers to: the base version, plus the techniques a line cannot
   * do without (the position of a loaded squat, the style of a swing).
   */
  private static String percentBaseKey(Spec spec, WodMovement line) {
    Set<String> kept = new TreeSet<>();
    for (String name : line.getTechniques()) {
      Technique technique = Technique.valueOf(name);
      TechniqueCategory category = technique.getCategory();
      boolean required =
          (category == TechniqueCategory.LOAD_POSITION
                  && spec.requiredRules().contains(RequiredRule.LOAD_POSITION_IF_EQUIPMENT)
                  && !BAR_POSITIONS.contains(name))
              || (category == TechniqueCategory.AMPLITUDE
                  && spec.requiredRules().contains(RequiredRule.AMPLITUDE))
              || (spec.requiredRules().contains(RequiredRule.SWING_STYLE)
                  && (technique == Technique.RUSSIAN || technique == Technique.AMERICAN));
      if (required) {
        kept.add(name);
      }
    }
    return LiftVariant.compose(Set.of(), kept);
  }

  /**
   * The name a line reads under in a list: its most precise usual name, else the movement's name.
   *
   * @param line The line, with its movement.
   * @return The name.
   */
  public static String listName(WodMovement line) {
    Movement movement = line.getMovement();
    Set<String> techniques = new LinkedHashSet<>(line.getTechniques());
    line.getComponentTechniques()
        .forEach(c -> techniques.add(c.componentOrder() + ":" + c.technique()));
    Set<String> equipment =
        visibleEquipment(Spec.of(movement), line.getEquipment(), line.getTechniques());
    return usualName(movement, equipment, techniques)
        .map(MovementUsualName::getNameEn)
        .orElse(movement.getName());
  }

  /**
   * The most precise usual name contained in a variant.
   *
   * @param movement The movement.
   * @param equipment Visible equipment of the variant.
   * @param techniques Techniques of the variant, per-component ones written {@code 1:POWER}.
   * @return The usual name, if any.
   */
  public static Optional<MovementUsualName> usualName(
      Movement movement, Set<String> equipment, Set<String> techniques) {
    Spec spec = Spec.of(movement);
    return movement.getUsualNames().stream()
        .filter(u -> techniques.containsAll(u.getTechniques()))
        .filter(
            u ->
                equipment.containsAll(
                    visibleEquipment(spec, u.getEquipment(), u.getTechniques())))
        .max(
            Comparator.comparingInt(
                    (MovementUsualName u) -> u.getTechniques().size() + u.getEquipment().size())
                .thenComparing(MovementUsualName::getNameEn));
  }

  /** The line's equipment without the one the movement implies. */
  static Set<String> visibleEquipment(
      Spec spec, Collection<String> equipment, Collection<String> techniques) {
    Set<String> visible = new TreeSet<>(equipment);
    if (spec.impliedEquipment() != null
        && (!spec.impliedRequiresPosition()
            || hasCategory(techniques.stream().filter(t -> !t.contains(":")).toList(), TechniqueCategory.LOAD_POSITION))) {
      visible.remove(spec.impliedEquipment().name());
    }
    return visible;
  }

  // -------------------------------------------------------------------------
  // Roles
  // -------------------------------------------------------------------------

  /**
   * Roles of a movement for a given line: its muscles, changed or completed by the adjustments of
   * the line's techniques and equipment; when several adjust one muscle, the lowest role wins.
   *
   * @param movement The movement.
   * @param techniques The line's techniques.
   * @param equipment The line's equipment.
   * @return Muscle to role.
   */
  public static Map<Muscle, MuscleRole> effectiveRoles(
      Movement movement, Collection<String> techniques, Collection<String> equipment) {
    Map<Muscle, MuscleRole> roles = new HashMap<>();
    for (MovementMuscle mm : movement.getTargetedMuscles()) {
      roles.put(mm.getMuscle(), mm.getRole());
    }
    Map<Muscle, MuscleRole> changes = new HashMap<>();
    for (MovementRoleAdjustment adjustment : movement.getRoleAdjustments()) {
      Collection<String> line =
          adjustment.getTriggerKind() == TriggerKind.TECHNIQUE ? techniques : equipment;
      if (line.contains(adjustment.getTrigger())) {
        changes.merge(adjustment.getMuscle(), adjustment.getRole(), CatalogRules::lowest);
      }
    }
    roles.putAll(changes);
    return roles;
  }

  private static MuscleRole lowest(MuscleRole a, MuscleRole b) {
    return a.ordinal() >= b.ordinal() ? a : b;
  }

  // -------------------------------------------------------------------------
  // Catalog entries
  // -------------------------------------------------------------------------

  /**
   * Checks a catalog movement before saving it.
   *
   * @param movement The movement, with its relations set.
   * @throws DataConflictException on the first rule broken.
   */
  public static void checkMovement(Movement movement) {
    checkNoCycle(movement);
    checkMuscleAndGroup(movement);
    checkAdjustmentTriggers(movement);
    checkUsualNames(movement);
    checkRolesAgainstJoints(movement);
  }

  private static void checkNoCycle(Movement movement) {
    Set<Movement> seen = new HashSet<>();
    Deque<Movement> todo = new ArrayDeque<>(parts(movement));
    while (!todo.isEmpty()) {
      Movement next = todo.pop();
      if (next == movement || (next.getId() != null && next.getId().equals(movement.getId()))) {
        throw new DataConflictException("error.movement.cycle", movement.getName());
      }
      if (seen.add(next)) {
        todo.addAll(parts(next));
      }
    }
  }

  private static Set<Movement> parts(Movement movement) {
    Set<Movement> parts = new LinkedHashSet<>(movement.getAcceptedMovements());
    movement.getComponents().stream().map(MovementComponent::getComponent).forEach(parts::add);
    return parts;
  }

  private static void checkMuscleAndGroup(Movement movement) {
    Set<Muscle> muscles =
        movement.getTargetedMuscles().stream()
            .map(MovementMuscle::getMuscle)
            .collect(Collectors.toSet());
    for (Muscle muscle : muscles) {
      if (muscle.getParent() != null && muscles.contains(muscle.getParent())) {
        throw new DataConflictException(
            "error.movement.muscle.and.group", muscle.getMedicalName(), movement.getName());
      }
    }
  }

  private static void checkAdjustmentTriggers(Movement movement) {
    for (MovementRoleAdjustment adjustment : movement.getRoleAdjustments()) {
      Set<? extends Enum<?>> allowed =
          adjustment.getTriggerKind() == TriggerKind.TECHNIQUE
              ? movement.getTechniques()
              : movement.getEquipment();
      if (allowed.stream().noneMatch(v -> v.name().equals(adjustment.getTrigger()))) {
        throw new DataConflictException(
            "error.movement.adjustment.trigger", adjustment.getTrigger(), movement.getName());
      }
    }
  }

  private static void checkUsualNames(Movement movement) {
    Set<String> techniques =
        movement.getTechniques().stream().map(Enum::name).collect(Collectors.toSet());
    movement
        .getComponents()
        .forEach(
            c ->
                c.getComponent()
                    .getTechniques()
                    .forEach(t -> techniques.add(c.getComponentOrder() + ":" + t.name())));
    Set<String> equipment =
        movement.getEquipment().stream().map(Enum::name).collect(Collectors.toSet());
    for (MovementUsualName name : movement.getUsualNames()) {
      if (!techniques.containsAll(name.getTechniques())
          || !equipment.containsAll(name.getEquipment())) {
        throw new DataConflictException("error.movement.usual.name.invalid", name.getNameEn());
      }
    }
  }

  /** An agonist or synergist must be able to act on a joint the movement uses. */
  private static void checkRolesAgainstJoints(Movement movement) {
    if (movement.getJointActions().isEmpty()) {
      return;
    }
    Set<JointAction> base =
        movement.getJointActions().stream()
            .filter(j -> j.trigger().isEmpty())
            .map(MovementJointAction::action)
            .collect(() -> EnumSet.noneOf(JointAction.class), Set::add, Set::addAll);
    for (MovementMuscle mm : movement.getTargetedMuscles()) {
      checkMuscleActs(movement, mm.getMuscle(), mm.getRole(), base);
    }
    for (MovementRoleAdjustment adjustment : movement.getRoleAdjustments()) {
      Set<JointAction> actions = EnumSet.copyOf(base);
      movement.getJointActions().stream()
          .filter(j -> adjustment.getTrigger().equals(j.trigger()))
          .forEach(j -> actions.add(j.action()));
      checkMuscleActs(movement, adjustment.getMuscle(), adjustment.getRole(), actions);
    }
  }

  private static void checkMuscleActs(
      Movement movement, Muscle muscle, MuscleRole role, Set<JointAction> actions) {
    if (role == MuscleRole.STABILIZER || muscle.getJointActions().isEmpty()) {
      return;
    }
    if (muscle.getJointActions().stream().noneMatch(actions::contains)) {
      throw new DataConflictException(
          "error.movement.role.without.action", muscle.getMedicalName(), movement.getName());
    }
  }
}
