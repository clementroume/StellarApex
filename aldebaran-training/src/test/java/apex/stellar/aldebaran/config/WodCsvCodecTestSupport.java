package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Unit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared fixtures for the WOD CSV tests (codec, seeding and export). */
public final class WodCsvCodecTestSupport {

  private WodCsvCodecTestSupport() {}

  /**
   * Returns the movement catalog used by the fixtures.
   *
   * @return Thruster, Pull-up and Running keyed by name.
   */
  public static Map<String, Movement> catalog() {
    return WodCsvCodecTest.CATALOG;
  }

  /**
   * Returns public WODs covering every CSV column, including nulls, multi-line text and commas.
   *
   * @return Two WODs with their movements.
   */
  public static List<Wod> sampleWods() {
    Wod fran =
        Wod.builder()
            .title("Fran")
            .wodType(WodType.FOR_TIME)
            .category(WodCategory.GIRLS)
            .scoreType(ScoreType.TIME)
            .repScheme("21-15-9")
            .descriptionEn("21-15-9 reps for time of:\n- thrusters\n- pull-ups")
            .isPublic(true)
            .modalities(Set.of(Modality.WEIGHTLIFTING, Modality.GYMNASTICS))
            .build();
    fran.getMovements()
        .add(
            WodMovement.builder()
                .wod(fran)
                .movement(WodCsvCodecTest.THRUSTER)
                .orderIndex(1)
                .repsScheme("21-15-9")
                .weight(95.0)
                .weightUnit(Unit.LBS)
                .notesEn("Women: 65 lb")
                .scalingOptionsEn("Lighter barbell, dumbbells")
                .equipment(Set.of("BARBELL"))
                .build());
    fran.getMovements()
        .add(
            WodMovement.builder()
                .wod(fran)
                .movement(WodCsvCodecTest.PULL_UP)
                .orderIndex(2)
                .repsScheme("21-15-9")
                .weightUnit(null)
                .distanceUnit(null)
                .durationDisplayUnit(null)
                .build());

    Wod chelsea =
        Wod.builder()
            .title("Chelsea")
            .wodType(WodType.EMOM)
            .category(WodCategory.GIRLS)
            .scoreType(ScoreType.ROUNDS)
            .repScheme("EMOM 30 min")
            .timeCapSeconds(1800)
            .emomInterval(60)
            .emomRounds(30)
            .notesEn("Score \"rounds\" completed")
            .isPublic(true)
            .modalities(Set.of(Modality.MONOSTRUCTURAL))
            .build();
    chelsea
        .getMovements()
        .add(
            WodMovement.builder()
                .wod(chelsea)
                .movement(WodCsvCodecTest.RUNNING)
                .orderIndex(1)
                .distance(0.25)
                .distanceUnit(Unit.MILES)
                .durationSeconds(45)
                .durationDisplayUnit(Unit.SECONDS)
                .calories(10)
                .techniques(Set.of("FORWARD", "WEIGHTED"))
                .build());
    return List.of(fran, chelsea);
  }

  /**
   * Asserts that two WODs hold the same CSV values.
   *
   * @param expected The original WOD.
   * @param actual The WOD read back.
   */
  public static void assertSameWod(Wod expected, Wod actual) {
    WodCsvCodecTest.assertSameWod(expected, actual);
  }
}
