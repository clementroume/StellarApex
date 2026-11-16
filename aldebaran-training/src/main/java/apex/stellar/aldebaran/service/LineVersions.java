package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.ScoreLine;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodScaledLine;
import apex.stellar.aldebaran.model.enums.Unit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The versions of a WOD line as detached copies (never saved): the line of the official Scaled
 * version (D141) and the line as done in a score (D142, the "lines done" the weekly balance reads,
 * D82). Every catalog rule written for a {@link WodMovement} (display, value of a set, modality,
 * quantity) then applies to them unchanged. Pure functions.
 */
public final class LineVersions {

  private LineVersions() {}

  /**
   * A detached copy of a line, with the same id (so that sets and lines of a score still find it).
   *
   * @param line The line.
   * @return The copy; its collections are new sets.
   */
  public static WodMovement copy(WodMovement line) {
    return WodMovement.builder()
        .id(line.getId())
        .wod(line.getWod())
        .movement(line.getMovement())
        .orderIndex(line.getOrderIndex())
        .complexGroup(line.getComplexGroup())
        .teamSplit(line.getTeamSplit())
        .segment(line.getSegment())
        .sets(line.getSets())
        .percentOfRm(line.getPercentOfRm())
        .rmReps(line.getRmReps())
        .repsScheme(line.getRepsScheme())
        .weight(line.getWeight())
        .weightFemale(line.getWeightFemale())
        .weightUnit(line.getWeightUnit())
        .durationSeconds(line.getDurationSeconds())
        .durationDisplayUnit(line.getDurationDisplayUnit())
        .distance(line.getDistance())
        .distanceUnit(line.getDistanceUnit())
        .calories(line.getCalories())
        .caloriesFemale(line.getCaloriesFemale())
        .height(line.getHeight())
        .heightFemale(line.getHeightFemale())
        .heightUnit(line.getHeightUnit())
        .length(line.getLength())
        .lengthUnit(line.getLengthUnit())
        .equipment(new HashSet<>(line.getEquipment()))
        .techniques(new HashSet<>(line.getTechniques()))
        .componentTechniques(new HashSet<>(line.getComponentTechniques()))
        .notesEn(line.getNotesEn())
        .notesFr(line.getNotesFr())
        .build();
  }

  /**
   * The line of the WOD's official Scaled version: the RX line with the values the version sets.
   * A changed movement or variant drops the per-component techniques (they belong to the RX
   * movement) and a percentage load replaced by a load.
   *
   * @param rx The RX line.
   * @return The line in the Scaled version, the RX line itself when the version keeps it.
   */
  public static WodMovement scaled(WodMovement rx) {
    WodScaledLine s = rx.getScaled();
    if (s == null) {
      return rx;
    }
    WodMovement line = copy(rx);
    applyVariant(line, rx, s.getMovement(), s.getEquipment(), s.getTechniques());
    if (s.getRepsScheme() != null) {
      line.setRepsScheme(s.getRepsScheme());
    }
    if (s.getWeight() != null || s.getWeightFemale() != null) {
      line.setWeight(s.getWeight());
      line.setWeightFemale(s.getWeightFemale());
      line.setWeightUnit(s.getWeightUnit() != null ? s.getWeightUnit() : rx.getWeightUnit());
      line.setPercentOfRm(null);
      line.setRmReps(null);
    }
    if (s.getDistance() != null) {
      line.setDistance(s.getDistance());
      line.setDistanceUnit(s.getDistanceUnit() != null ? s.getDistanceUnit() : rx.getDistanceUnit());
    }
    if (s.getCalories() != null || s.getCaloriesFemale() != null) {
      line.setCalories(s.getCalories());
      line.setCaloriesFemale(s.getCaloriesFemale());
    }
    if (s.getHeight() != null || s.getHeightFemale() != null) {
      line.setHeight(s.getHeight());
      line.setHeightFemale(s.getHeightFemale());
      line.setHeightUnit(s.getHeightUnit() != null ? s.getHeightUnit() : rx.getHeightUnit());
    }
    if (s.getDurationSeconds() != null) {
      line.setDurationSeconds(s.getDurationSeconds());
    }
    return line;
  }

  /**
   * A line as done in a score: the RX line with what the athlete changed. The load done is the
   * athlete's own (kg, both men's and women's values), distances and heights in meters.
   *
   * @param rx The RX line.
   * @param done The line done otherwise than prescribed, or null (done as prescribed).
   * @return The line as done, the RX line itself when nothing changed.
   */
  public static WodMovement done(WodMovement rx, @Nullable ScoreLine done) {
    if (done == null) {
      return rx;
    }
    WodMovement line = copy(rx);
    applyVariant(
        line,
        rx,
        done.getMovement() != null ? done.getMovement() : rx.getMovement(),
        done.getEquipment(),
        done.getTechniques());
    if (done.getRepsScheme() != null) {
      line.setRepsScheme(done.getRepsScheme());
    }
    if (done.getWeightKg() != null) {
      line.setWeight(done.getWeightKg());
      line.setWeightFemale(done.getWeightKg());
      line.setWeightUnit(Unit.KG);
      line.setPercentOfRm(null);
      line.setRmReps(null);
    }
    if (done.getDistanceMeters() != null) {
      line.setDistance(done.getDistanceMeters());
      line.setDistanceUnit(Unit.METERS);
    }
    if (done.getCalories() != null) {
      line.setCalories(done.getCalories());
      line.setCaloriesFemale(done.getCalories());
    }
    if (done.getHeightMeters() != null) {
      line.setHeight(done.getHeightMeters());
      line.setHeightFemale(done.getHeightMeters());
      line.setHeightUnit(Unit.METERS);
    }
    if (done.getDurationSeconds() != null) {
      line.setDurationSeconds(done.getDurationSeconds());
    }
    return line;
  }

  /**
   * The lines of a WOD as done in a score, in the WOD's order (lines without change are the RX
   * lines themselves).
   *
   * @param rxLines The WOD's lines.
   * @param done The score's changed lines.
   * @return The lines as done.
   */
  public static List<WodMovement> done(List<WodMovement> rxLines, List<ScoreLine> done) {
    return rxLines.stream().map(rx -> done(rx, lineOf(done, rx))).toList();
  }

  /**
   * The changed line of a score for a WOD line.
   *
   * @param done The score's changed lines.
   * @param rx The WOD line.
   * @return The changed line, or null.
   */
  public static @Nullable ScoreLine lineOf(List<ScoreLine> done, WodMovement rx) {
    if (done == null) {
      return null;
    }
    for (ScoreLine l : done) {
      if (l.getWodMovement() != null && l.getWodMovement().getId() != null
          && l.getWodMovement().getId().equals(rx.getId())) {
        return l;
      }
      if (l.getWodMovement() == rx) {
        return l;
      }
    }
    return null;
  }

  private static void applyVariant(
      WodMovement line,
      WodMovement rx,
      Movement movement,
      Set<String> equipment,
      Set<String> techniques) {
    boolean sameMovement =
        movement == null
            || movement == rx.getMovement()
            || (movement.getId() != null
                && rx.getMovement() != null
                && movement.getId().equals(rx.getMovement().getId()));
    boolean sameVariant =
        sameMovement
            && new LinkedHashSet<>(equipment).equals(new LinkedHashSet<>(rx.getEquipment()))
            && new LinkedHashSet<>(techniques).equals(new LinkedHashSet<>(rx.getTechniques()));
    if (sameVariant) {
      return;
    }
    line.setMovement(movement != null ? movement : rx.getMovement());
    line.setEquipment(new HashSet<>(equipment));
    line.setTechniques(new HashSet<>(techniques));
    line.getComponentTechniques().clear();
  }
}
