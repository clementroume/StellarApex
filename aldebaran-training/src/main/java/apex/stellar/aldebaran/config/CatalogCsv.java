package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.DumbbellBase;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementJointAction;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment.TriggerKind;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.enums.JointAction;
import apex.stellar.aldebaran.model.enums.JointAction.Regime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Temporary development tool: how the catalog model is written in the git-ignored {@code
 * movements.csv} columns. Shared by the seeder and the exporter so a round-trip is lossless.
 */
public final class CatalogCsv {

  /** Usual names file in {@code src/main/resources}. */
  public static final String USUAL_NAMES_FILE = "movement_usual_names.csv";

  /** Columns of the usual names file. */
  public static final String[] USUAL_NAME_HEADERS = {
    "movement", "name_en", "name_fr", "search_terms", "techniques", "equipment",
    "description_en", "description_fr", "video_url"
  };

  private CatalogCsv() {}

  /**
   * Parses {@code FRONT_RACK=2, OVERHEAD=1} ({@code =2}: no position, {@code =}: free).
   *
   * @param cell The cell.
   * @return The bases.
   */
  public static Set<DumbbellBase> parseBases(String cell) {
    return items(cell, ",").stream()
        .map(
            item -> {
              String[] parts = item.split("=", -1);
              return new DumbbellBase(
                  parts[0].trim(), parts[1].isBlank() ? null : Integer.valueOf(parts[1].trim()));
            })
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Writes dumbbell bases.
   *
   * @param bases The bases.
   * @return The cell.
   */
  public static String formatBases(Set<DumbbellBase> bases) {
    return bases.stream()
        .map(b -> b.loadPosition() + "=" + (b.implementCount() == null ? "" : b.implementCount()))
        .sorted()
        .collect(Collectors.joining(", "));
  }

  /**
   * Parses {@code HIP_EXT:DYNAMIC, SUMO>HIP_ADD:HELD}.
   *
   * @param cell The cell.
   * @return The joint actions.
   */
  public static Set<MovementJointAction> parseJoints(String cell) {
    return items(cell, ",").stream()
        .map(
            item -> {
              String[] trigger = item.split(">", 2);
              String[] action = trigger[trigger.length - 1].split(":", 2);
              return new MovementJointAction(
                  trigger.length == 2 ? trigger[0].trim() : "",
                  JointAction.valueOf(action[0].trim()),
                  Regime.valueOf(action[1].trim()));
            })
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Writes joint actions.
   *
   * @param joints The joint actions.
   * @return The cell.
   */
  public static String formatJoints(Set<MovementJointAction> joints) {
    return joints.stream()
        .map(
            j ->
                (j.trigger().isEmpty() ? "" : j.trigger() + ">")
                    + j.action().name()
                    + ":"
                    + j.regime().name())
        .sorted()
        .collect(Collectors.joining(", "));
  }

  /**
   * Parses {@code Clean|SQUAT||true; Thruster|||false} into steps of a chain.
   *
   * @param cell The cell.
   * @param chain The chain.
   * @param movements Movements by name.
   * @return The steps, in order.
   */
  public static List<MovementComponent> parseComponents(
      String cell, Movement chain, Function<String, Movement> movements) {
    List<String> parts = items(cell, ";");
    return java.util.stream.IntStream.range(0, parts.size())
        .mapToObj(
            i -> {
              String[] fields = parts.get(i).split("\\|", -1);
              return MovementComponent.builder()
                  .chain(chain)
                  .componentOrder(i + 1)
                  .component(movements.apply(fields[0].trim()))
                  .techniques(words(fields[1]))
                  .equipment(words(fields[2]))
                  .countsForRecords(Boolean.parseBoolean(fields[3].trim()))
                  .build();
            })
        .toList();
  }

  /**
   * Writes the steps of a chain.
   *
   * @param components The steps.
   * @return The cell.
   */
  public static String formatComponents(List<MovementComponent> components) {
    return components.stream()
        .map(
            c ->
                c.getComponent().getName()
                    + "|"
                    + String.join(" ", c.getTechniques())
                    + "|"
                    + String.join(" ", c.getEquipment())
                    + "|"
                    + c.isCountsForRecords())
        .collect(Collectors.joining("; "));
  }

  /**
   * Parses {@code TECHNIQUE:SQUAT=Adductor Magnus:SYNERGIST; ...}.
   *
   * @param cell The cell.
   * @param movement The movement.
   * @param muscles Muscles by lower-case medical name.
   * @return The adjustments.
   */
  public static Set<MovementRoleAdjustment> parseAdjustments(
      String cell, Movement movement, Map<String, Muscle> muscles) {
    return items(cell, ";").stream()
        .map(
            item -> {
              String[] sides = item.split("=", 2);
              String[] trigger = sides[0].split(":", 2);
              String[] target = sides[1].split(":", 2);
              Muscle muscle = muscles.get(target[0].trim().toLowerCase());
              if (muscle == null) {
                throw new IllegalArgumentException("Unknown muscle in adjustment: " + item);
              }
              return MovementRoleAdjustment.builder()
                  .movement(movement)
                  .triggerKind(TriggerKind.valueOf(trigger[0].trim()))
                  .trigger(trigger[1].trim())
                  .muscle(muscle)
                  .role(MuscleRole.valueOf(target[1].trim()))
                  .build();
            })
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Writes role adjustments.
   *
   * @param adjustments The adjustments.
   * @return The cell.
   */
  public static String formatAdjustments(Set<MovementRoleAdjustment> adjustments) {
    return adjustments.stream()
        .map(
            a ->
                a.getTriggerKind().name()
                    + ":"
                    + a.getTrigger()
                    + "="
                    + a.getMuscle().getMedicalName()
                    + ":"
                    + a.getRole().name())
        .sorted()
        .collect(Collectors.joining("; "));
  }

  /**
   * Splits a space-separated cell.
   *
   * @param cell The cell, possibly null.
   * @return Its words.
   */
  public static Set<String> words(String cell) {
    if (cell == null || cell.isBlank()) {
      return new LinkedHashSet<>();
    }
    return Arrays.stream(cell.trim().split("\\s+"))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Splits a cell on a separator.
   *
   * @param cell The cell, possibly null.
   * @param separator The separator.
   * @return Its trimmed items.
   */
  public static List<String> items(String cell, String separator) {
    if (cell == null || cell.isBlank()) {
      return List.of();
    }
    return Arrays.stream(cell.split(separator)).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
