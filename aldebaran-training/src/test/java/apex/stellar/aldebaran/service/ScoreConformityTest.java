package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.ScoreLine;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodScaledLine;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.service.ScoreConformity.Context;
import apex.stellar.aldebaran.service.ScoreConformity.Result;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoreConformityTest {

  private static final Movement THRUSTER = Movement.builder().id(1L).name("Thruster").build();
  private static final Movement PULL_UP = Movement.builder().id(2L).name("Pull-up").build();
  private static final Movement RING_ROW = Movement.builder().id(3L).name("Ring Row").build();

  private static final Context MEN_KG = new Context(Division.MEN, Unit.KG, false, true);

  private WodMovement thruster;
  private WodMovement pullUp;
  private List<WodMovement> fran;

  @BeforeEach
  void fran() {
    Wod wod = Wod.builder().id(9L).build();
    thruster =
        WodMovement.builder()
            .id(10L)
            .wod(wod)
            .movement(THRUSTER)
            .orderIndex(1)
            .repsScheme("21-15-9")
            .weight(95.0)
            .weightFemale(65.0)
            .weightUnit(Unit.LBS)
            .equipment(new HashSet<>(Set.of("BARBELL")))
            .build();
    pullUp =
        WodMovement.builder()
            .id(11L)
            .wod(wod)
            .movement(PULL_UP)
            .orderIndex(2)
            .repsScheme("21-15-9")
            .build();
    fran = List.of(thruster, pullUp);
  }

  private static ScoreLine line(WodMovement rx, Movement movement) {
    return ScoreLine.builder()
        .wodMovement(rx)
        .movement(movement)
        .equipment(new HashSet<>(rx.getEquipment()))
        .techniques(new HashSet<>(rx.getTechniques()))
        .build();
  }

  @Test
  @DisplayName("Nothing changed: as prescribed, no badge, no official version")
  void asPrescribed() {
    Result result = ScoreConformity.of(fran, List.of(), MEN_KG);
    assertThat(result.asPrescribed()).isTrue();
    assertThat(result.rxPlus()).isFalse();
    assertThat(result.official()).isFalse();
  }

  @Test
  @DisplayName("95 lb thruster: 40 kg (the box value) and 43 kg are RX, 35 kg is lighter")
  void boxEquivalence() {
    ScoreLine forty = line(thruster, THRUSTER);
    forty.setWeightKg(40.0);
    assertThat(ScoreConformity.of(fran, List.of(forty), MEN_KG).asPrescribed()).isTrue();

    ScoreLine exact = line(thruster, THRUSTER);
    exact.setWeightKg(43.0);
    assertThat(ScoreConformity.of(fran, List.of(exact), MEN_KG).asPrescribed()).isTrue();

    ScoreLine light = line(thruster, THRUSTER);
    light.setWeightKg(35.0);
    Result result = ScoreConformity.of(fran, List.of(light), MEN_KG);
    assertThat(result.asPrescribed()).isFalse();
    assertThat(result.rxPlus()).isFalse();
  }

  @Test
  @DisplayName("Women: their own load; without division, either load is RX")
  void divisions() {
    ScoreLine thirty = line(thruster, THRUSTER);
    thirty.setWeightKg(30.0);
    assertThat(
            ScoreConformity.of(fran, List.of(thirty), new Context(Division.WOMEN, Unit.KG, false, true))
                .asPrescribed())
        .isTrue();
    assertThat(ScoreConformity.of(fran, List.of(thirty), MEN_KG).asPrescribed()).isFalse();
    assertThat(
            ScoreConformity.of(fran, List.of(thirty), new Context(null, Unit.KG, false, true))
                .asPrescribed())
        .isTrue();
  }

  @Test
  @DisplayName("Rx+: a heavier load, every other line RX; a substitute or fewer reps are lighter")
  void rxPlus() {
    ScoreLine heavy = line(thruster, THRUSTER);
    heavy.setWeightKg(50.0);
    assertThat(ScoreConformity.of(fran, List.of(heavy), MEN_KG).rxPlus()).isTrue();

    ScoreLine ringRows = line(pullUp, RING_ROW);
    assertThat(ScoreConformity.of(fran, List.of(heavy, ringRows), MEN_KG).rxPlus()).isFalse();

    ScoreLine fewer = line(pullUp, PULL_UP);
    fewer.setRepsScheme("15-12-9");
    Result result = ScoreConformity.of(fran, List.of(fewer), MEN_KG);
    assertThat(result.asPrescribed()).isFalse();
    assertThat(result.rxPlus()).isFalse();
  }

  @Test
  @DisplayName("Rx+: a variant that counts for the prescribed one (Chest-to-bar for a Pull-up)")
  void countsFor() {
    ScoreLine chestToBar = line(pullUp, PULL_UP);
    chestToBar.getTechniques().add("CHEST_TO_BAR");
    assertThat(ScoreConformity.of(fran, List.of(chestToBar), MEN_KG).rxPlus()).isTrue();
  }

  @Test
  @DisplayName("Official Scaled version: every line as the version (the lines it keeps RX too)")
  void official() {
    thruster.setScaled(
        WodScaledLine.builder()
            .movement(THRUSTER)
            .equipment(new HashSet<>(Set.of("BARBELL")))
            .techniques(new HashSet<>())
            .weight(65.0)
            .weightFemale(45.0)
            .weightUnit(Unit.LBS)
            .build());
    ScoreLine thirty = line(thruster, THRUSTER);
    thirty.setWeightKg(30.0);
    Result result = ScoreConformity.of(fran, List.of(thirty), MEN_KG);
    assertThat(result.official()).isTrue();
    assertThat(result.asPrescribed()).isFalse();

    ScoreLine ringRows = line(pullUp, RING_ROW);
    assertThat(ScoreConformity.of(fran, List.of(thirty, ringRows), MEN_KG).official()).isFalse();
  }

  @Test
  @DisplayName("A count on a max line is not a change (D147)")
  void maxCount() {
    pullUp.setRepsScheme("max");
    ScoreLine counted = line(pullUp, PULL_UP);
    counted.setRepsScheme("61");
    assertThat(ScoreConformity.of(fran, List.of(counted), MEN_KG).asPrescribed()).isTrue();
  }

  @Test
  @DisplayName("The round as done: Cindy at 3 pull-ups is 28 reps")
  void roundReps() {
    pullUp.setRepsScheme("5");
    ScoreLine three = line(pullUp, PULL_UP);
    three.setRepsScheme("3");
    assertThat(LineVersions.roundReps(30, fran, List.of(three))).isEqualTo(28);
    assertThat(LineVersions.roundReps(30, fran, List.of())).isEqualTo(30);
    assertThat(LineVersions.roundReps(null, fran, List.of(three))).isNull();
  }
}
