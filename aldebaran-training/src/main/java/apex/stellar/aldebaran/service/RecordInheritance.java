package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementRecord;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Technique.TechniqueCategory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Records are stored at the most precise level and read as the best of the records that count for
 * them: a Power Clean counts for the Clean, a Clean and Jerk for the Clean and the Jerk, a Jerk for
 * Shoulder-to-Overhead. Pure function of the stored records and the catalog.
 */
final class RecordInheritance {

  /**
   * A movement and variant a performance also proves.
   *
   * @param movement The movement proved.
   * @param variantKey Its variant key.
   */
  record Target(Movement movement, String variantKey) {}

  /**
   * The record read for a slot.
   *
   * @param movement The movement of the slot.
   * @param variantKey The variant of the slot.
   * @param source The stored record it comes from.
   * @param inherited Whether the stored record belongs to another variant or movement.
   */
  record Effective(Movement movement, String variantKey, MovementRecord source, boolean inherited) {}

  private static final Comparator<Effective> BEST_FIRST =
      Comparator.comparingDouble((Effective e) -> e.source().getWeightKg())
          .reversed()
          .thenComparing(Effective::inherited)
          .thenComparing(e -> e.source().getDate());

  private RecordInheritance() {}

  /**
   * The effective records of a set of stored ones.
   *
   * @param stored The stored records of one athlete.
   * @param choices The "either" movements of the catalog.
   * @return Best record per movement, variant and reps.
   */
  static List<Effective> effective(Collection<MovementRecord> stored, Collection<Movement> choices) {
    Map<String, Effective> best = new LinkedHashMap<>();
    for (MovementRecord record : stored) {
      for (Target target : expand(record.getMovement(), record.getVariantKey(), choices)) {
        boolean inherited =
            !target.movement().equals(record.getMovement())
                || !target.variantKey().equals(record.getVariantKey());
        Effective candidate = new Effective(target.movement(), target.variantKey(), record, inherited);
        String slot = target.movement().getId() + "#" + target.variantKey() + "#" + record.getReps();
        best.merge(slot, candidate, (a, b) -> BEST_FIRST.compare(a, b) <= 0 ? a : b);
      }
    }
    return new ArrayList<>(best.values());
  }

  /**
   * Every movement and variant a performance proves, itself included.
   *
   * @param movement The movement performed.
   * @param variantKey Its variant key.
   * @param choices The "either" movements of the catalog.
   * @return The targets.
   */
  static Set<Target> expand(Movement movement, String variantKey, Collection<Movement> choices) {
    Set<Target> reached = new HashSet<>();
    visit(movement, variantKey, choices, reached);
    return reached;
  }

  private static void visit(
      Movement movement, String variantKey, Collection<Movement> choices, Set<Target> reached) {
    for (String key : sameMovementTargets(movement, variantKey)) {
      if (!reached.add(new Target(movement, key))) {
        continue;
      }
      // Only the variant really performed goes to the components: its own targets follow there
      if (key.equals(variantKey)) {
        for (MovementComponent component : movement.getComponents()) {
          if (component.isCountsForRecords()) {
            visit(
                component.getComponent(),
                componentKey(movement, component, variantKey),
                choices,
                reached);
          }
        }
      }
      if (provesBase(key)) {
        for (Movement choice : choices) {
          if (choice.getAcceptedMovements().contains(movement)) {
            reached.add(new Target(choice, LiftVariant.key(choice, LiftVariant.equipmentOf(key), Set.of())));
          }
        }
      }
    }
  }

  /** Base version, whatever the equipment: {@code PUSH} is a way of doing the base press. */
  private static boolean provesBase(String key) {
    Set<String> techniques = LiftVariant.techniquesOf(key);
    return techniques.isEmpty() || techniques.equals(Set.of(Technique.PUSH.name()));
  }

  /** The variants of the same movement a key proves: whole-line and per-component dominance. */
  private static Set<String> sameMovementTargets(Movement movement, String variantKey) {
    Set<String> equipment = LiftVariant.equipmentOf(variantKey);
    Map<Integer, Set<Technique>> byPart = new TreeMap<>();
    byPart.put(0, new HashSet<>());
    for (String token : LiftVariant.techniquesOf(variantKey)) {
      int colon = token.indexOf(':');
      int part = colon < 0 ? 0 : Integer.parseInt(token.substring(0, colon));
      byPart
          .computeIfAbsent(part, k -> new HashSet<>())
          .add(Technique.valueOf(token.substring(colon + 1)));
    }
    List<Set<String>> combos = List.of(Set.of());
    for (Map.Entry<Integer, Set<Technique>> part : byPart.entrySet()) {
      List<Set<String>> next = new ArrayList<>();
      for (Set<Technique> variant :
          VariantDominance.targets(part.getValue(), allowed(movement, part.getKey()))) {
        for (Set<String> combo : combos) {
          Set<String> grown = new TreeSet<>(combo);
          variant.forEach(t -> grown.add(part.getKey() == 0 ? t.name() : part.getKey() + ":" + t.name()));
          next.add(grown);
        }
      }
      combos = next;
    }
    Set<String> keys = new HashSet<>();
    combos.forEach(techniques -> keys.add(LiftVariant.compose(equipment, techniques)));
    keys.add(variantKey);
    return keys;
  }

  private static Set<Technique> allowed(Movement movement, int part) {
    if (part == 0) {
      return movement.getTechniques();
    }
    return movement.getComponents().stream()
        .filter(c -> c.getComponentOrder() == part)
        .findFirst()
        .map(c -> c.getComponent().getTechniques())
        .orElse(Set.of());
  }

  /** The variant of a component inside a performance of its chain. */
  private static String componentKey(
      Movement chain, MovementComponent component, String chainKey) {
    Set<String> techniques = new TreeSet<>(component.getTechniques());
    Set<String> allowed = new HashSet<>();
    component.getComponent().getTechniques().forEach(t -> allowed.add(t.name()));
    for (String token : LiftVariant.techniquesOf(chainKey)) {
      int colon = token.indexOf(':');
      if (colon >= 0) {
        if (Integer.parseInt(token.substring(0, colon)) == component.getComponentOrder()) {
          techniques.add(token.substring(colon + 1));
        }
        continue;
      }
      TechniqueCategory category = Technique.valueOf(token).getCategory();
      boolean first = component.getComponentOrder() == 1;
      boolean carried =
          category == TechniqueCategory.LATERALITY
              || (first && category == TechniqueCategory.STARTING_POSITION);
      if (carried && allowed.contains(token)) {
        techniques.add(token);
      }
    }
    Set<String> equipment =
        component.getEquipment().isEmpty()
            ? new HashSet<>(LiftVariant.equipmentOf(chainKey))
            : component.getEquipment();
    if (equipment.isEmpty() && chain.getImpliedEquipment() != null) {
      equipment = Set.of(chain.getImpliedEquipment().name());
    }
    return LiftVariant.key(component.getComponent(), equipment, techniques);
  }

  /**
   * The movements whose performances can prove a movement, itself included.
   *
   * @param movement The movement read.
   * @param chainsOf Chains that count for a movement.
   * @return The source movements.
   */
  static Set<Movement> sources(
      Movement movement, java.util.function.Function<Movement, List<Movement>> chainsOf) {
    Set<Movement> found = new HashSet<>();
    List<Movement> todo = new ArrayList<>(List.of(movement));
    Map<Movement, Boolean> done = new HashMap<>();
    while (!todo.isEmpty()) {
      Movement next = todo.removeLast();
      if (done.putIfAbsent(next, true) != null) {
        continue;
      }
      found.add(next);
      todo.addAll(next.getAcceptedMovements());
      todo.addAll(chainsOf.apply(next));
    }
    return found;
  }
}
