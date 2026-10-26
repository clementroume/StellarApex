package apex.stellar.aldebaran.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.exception.InvalidWodMovementException;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Unit;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link WodCsvCodec}. */
class WodCsvCodecTest {

  static final Movement THRUSTER =
      Movement.builder()
          .id(1L)
          .name("Thruster")
          .category(Category.COMPLEXES)
          .equipment(Set.of(Equipment.BARBELL, Equipment.DUMBBELL))
          .techniques(Set.of(Technique.PAUSED, Technique.TEMPO))
          .build();
  static final Movement PULL_UP =
      Movement.builder().id(2L).name("Pull-up").category(Category.PULLING).build();
  static final Movement RUNNING =
      Movement.builder()
          .id(3L)
          .name("Running")
          .category(Category.CARDIO)
          .techniques(Set.of(Technique.FORWARD, Technique.WEIGHTED))
          .build();

  static final Map<String, Movement> CATALOG =
      WodCsvCodec.byName(List.of(THRUSTER, PULL_UP, RUNNING));

  static final String WODS_CSV =
      """
      title,wod_type,category,score_type,rep_scheme,time_cap_seconds,emom_interval,emom_rounds,description,notes
      Fran,FOR_TIME,GIRLS,TIME,21-15-9,,,,"21-15-9 reps for time
      of thrusters and pull-ups.",
      Cindy,amrap,,ROUNDS_REPS,AMRAP 20 min,1200,,,,"Note, with comma"
      """;

  static final String WOD_MOVEMENTS_CSV =
      """
      wod_title,order_index,movement_name,reps_scheme,weight,weight_unit,distance,distance_unit,duration_seconds,calories,notes,scaling_options
      Fran,2,Pull-up,21-15-9,,,,,,,,Ring rows
      Fran,1,Thruster,21-15-9,95,LBS,,,,,Women: 65 lb,
      Fran,3,Wall Climb,10,,,,,,,,
      Ghost,1,Pull-up,5,,,,,,,,
      Cindy,1,Running,,,,400,METERS,,,,
      """;

  @Test
  @DisplayName("Parses public WODs with their movements sorted by order index")
  void shouldParseWods() throws Exception {
    List<Wod> wods =
        WodCsvCodec.read(
            new StringReader(WODS_CSV), new StringReader(WOD_MOVEMENTS_CSV), CATALOG);

    assertEquals(2, wods.size());
    Wod fran = wods.getFirst();
    assertEquals("Fran", fran.getTitle());
    assertEquals(WodType.FOR_TIME, fran.getWodType());
    assertEquals(WodCategory.GIRLS, fran.getCategory());
    assertEquals(ScoreType.TIME, fran.getScoreType());
    assertEquals("21-15-9", fran.getRepScheme());
    assertEquals("21-15-9 reps for time\nof thrusters and pull-ups.", fran.getDescriptionEn());
    assertNull(fran.getNotesEn());
    assertNull(fran.getTimeCapSeconds());
    assertTrue(fran.isPublic());
    assertNull(fran.getAuthorId());
    assertNull(fran.getGymId());
    assertEquals(Set.of(Modality.WEIGHTLIFTING, Modality.GYMNASTICS), fran.getModalities());

    // Unknown movement row skipped, the others kept and sorted
    assertEquals(2, fran.getMovements().size());
    WodMovement thruster = fran.getMovements().getFirst();
    assertSame(THRUSTER, thruster.getMovement());
    assertSame(fran, thruster.getWod());
    assertEquals(1, thruster.getOrderIndex());
    assertEquals(95.0, thruster.getWeight());
    assertEquals(Unit.LBS, thruster.getWeightUnit());
    assertEquals("Women: 65 lb", thruster.getNotesEn());
    assertNull(thruster.getScalingOptionsEn());
    WodMovement pullUp = fran.getMovements().get(1);
    assertSame(PULL_UP, pullUp.getMovement());
    assertNull(pullUp.getWeight());
    assertNull(pullUp.getWeightUnit());
    assertEquals("Ring rows", pullUp.getScalingOptionsEn());

    Wod cindy = wods.get(1);
    assertEquals(WodType.AMRAP, cindy.getWodType());
    assertNull(cindy.getCategory());
    assertEquals(1200, cindy.getTimeCapSeconds());
    assertEquals("Note, with comma", cindy.getNotesEn());
    WodMovement run = cindy.getMovements().getFirst();
    assertEquals(400.0, run.getDistance());
    assertEquals(Unit.METERS, run.getDistanceUnit());
    assertNull(run.getRepsScheme());
  }

  @Test
  @DisplayName("Rejects a WOD row with an unknown type")
  void shouldRejectUnknownWodType() {
    String wods = "title,wod_type,score_type\nFran,NOPE,TIME\n";

    assertThrows(
        IllegalArgumentException.class,
        () ->
            WodCsvCodec.read(
                new StringReader(wods), new StringReader("wod_title\n"), Map.of()));
  }

  @Test
  @DisplayName("Rejects a movement variant that the movement does not allow")
  void shouldRejectVariantNotAllowedByMovement() {
    String movements =
        "wod_title,order_index,movement_name,equipment,techniques\n"
            + "Fran,1,Thruster,KETTLEBELL,\n";

    InvalidWodMovementException ex =
        assertThrows(
            InvalidWodMovementException.class,
            () ->
                WodCsvCodec.read(
                    new StringReader(WODS_CSV), new StringReader(movements), CATALOG));
    assertEquals("error.wod.movement.equipment.invalid", ex.getMessageKey());
  }

  @Test
  @DisplayName("Write then read gives back the same values")
  void shouldRoundTrip() throws Exception {
    Wod wod =
        Wod.builder()
            .title("Josh")
            .wodType(WodType.FOR_TIME)
            .category(WodCategory.HERO)
            .scoreType(ScoreType.TIME)
            .repScheme("21-42 / 15-30")
            .timeCapSeconds(600)
            .emomInterval(60)
            .emomRounds(10)
            .descriptionEn("Line 1\nLine 2, with \"quotes\"")
            .notesEn(" padded ")
            .descriptionFr("Ligne 1\nLigne 2, avec des « guillemets »")
            .notesFr("En mémoire, avec une virgule")
            .isPublic(true)
            .build();
    wod.getMovements()
        .add(
            WodMovement.builder()
                .wod(wod)
                .movement(PULL_UP)
                .orderIndex(2)
                .repsScheme("42")
                .weight(null)
                .weightUnit(null)
                .distanceUnit(null)
                .durationDisplayUnit(null)
                .build());
    wod.getMovements()
        .add(
            WodMovement.builder()
                .wod(wod)
                .movement(THRUSTER)
                .orderIndex(1)
                .repsScheme("21")
                .weight(42.5)
                .weightUnit(Unit.KG)
                .distance(1.5)
                .distanceUnit(Unit.MILES)
                .durationSeconds(90)
                .durationDisplayUnit(Unit.MINUTES)
                .calories(12)
                .weightFemale(30.0)
                .caloriesFemale(9)
                .height(60.0)
                .heightFemale(50.0)
                .heightUnit(Unit.CENTIMETERS)
                .notesEn("Two dumbbells")
                .notesFr("Deux haltères")
                .scalingOptionsEn("PVC")
                .scalingOptionsFr("Tube PVC")
                .equipment(Set.of("BARBELL", "DUMBBELL"))
                .techniques(Set.of("PAUSED"))
                .build());

    StringWriter wodsOut = new StringWriter();
    StringWriter movementsOut = new StringWriter();
    WodCsvCodec.write(List.of(wod), wodsOut, movementsOut);
    List<Wod> read =
        WodCsvCodec.read(
            new StringReader(wodsOut.toString()),
            new StringReader(movementsOut.toString()),
            CATALOG);

    assertEquals(1, read.size());
    assertSameWod(wod, read.getFirst());
  }

  /** Compares every field stored in the CSV files. */
  static void assertSameWod(Wod expected, Wod actual) {
    assertEquals(expected.getTitle(), actual.getTitle());
    assertEquals(expected.getWodType(), actual.getWodType());
    assertEquals(expected.getCategory(), actual.getCategory());
    assertEquals(expected.getScoreType(), actual.getScoreType());
    assertEquals(expected.getRepScheme(), actual.getRepScheme());
    assertEquals(expected.getTimeCapSeconds(), actual.getTimeCapSeconds());
    assertEquals(expected.getEmomInterval(), actual.getEmomInterval());
    assertEquals(expected.getEmomRounds(), actual.getEmomRounds());
    assertEquals(expected.getDescriptionEn(), actual.getDescriptionEn());
    assertEquals(expected.getNotesEn(), actual.getNotesEn());
    assertEquals(expected.getDescriptionFr(), actual.getDescriptionFr());
    assertEquals(expected.getNotesFr(), actual.getNotesFr());
    assertEquals(expected.isPublic(), actual.isPublic());

    List<WodMovement> sorted =
        expected.getMovements().stream()
            .sorted((a, b) -> a.getOrderIndex().compareTo(b.getOrderIndex()))
            .toList();
    assertEquals(sorted.size(), actual.getMovements().size());
    for (int i = 0; i < sorted.size(); i++) {
      WodMovement e = sorted.get(i);
      WodMovement a = actual.getMovements().get(i);
      assertEquals(e.getMovement().getName(), a.getMovement().getName());
      assertEquals(e.getOrderIndex(), a.getOrderIndex());
      assertEquals(e.getRepsScheme(), a.getRepsScheme());
      assertEquals(e.getWeight(), a.getWeight());
      assertEquals(e.getWeightUnit(), a.getWeightUnit());
      assertEquals(e.getDistance(), a.getDistance());
      assertEquals(e.getDistanceUnit(), a.getDistanceUnit());
      assertEquals(e.getDurationSeconds(), a.getDurationSeconds());
      assertEquals(e.getDurationDisplayUnit(), a.getDurationDisplayUnit());
      assertEquals(e.getCalories(), a.getCalories());
      assertEquals(e.getNotesEn(), a.getNotesEn());
      assertEquals(e.getScalingOptionsEn(), a.getScalingOptionsEn());
      assertEquals(e.getWeightFemale(), a.getWeightFemale());
      assertEquals(e.getCaloriesFemale(), a.getCaloriesFemale());
      assertEquals(e.getHeight(), a.getHeight());
      assertEquals(e.getHeightFemale(), a.getHeightFemale());
      assertEquals(e.getHeightUnit(), a.getHeightUnit());
      assertEquals(e.getNotesFr(), a.getNotesFr());
      assertEquals(e.getScalingOptionsFr(), a.getScalingOptionsFr());
      assertEquals(e.getEquipment(), a.getEquipment());
      assertEquals(e.getTechniques(), a.getTechniques());
    }
  }
}
