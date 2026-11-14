package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Measure;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.service.LinePrescription.Kind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinePrescriptionTest {

  private static LinePrescription read(String scheme) {
    return LinePrescription.of(WodMovement.builder().repsScheme(scheme).build(), null);
  }

  @Test
  @DisplayName("The six written forms of D121")
  void forms() {
    assertThat(read("21").values()).containsExactly(21.0);
    assertThat(read("21-15-9").isScheme()).isTrue();
    assertThat(read("21-15-9").total()).isEqualTo(45);
    assertThat(read("MAX").kind()).isEqualTo(Kind.MAX);
    assertThat(read("1 min").measure()).isEqualTo(Measure.SECONDS);
    assertThat(read("1 min").total()).isEqualTo(60);
    assertThat(read("30 s").total()).isEqualTo(30);
    LinePrescription ladder = read("3-6-9-...");
    assertThat(ladder.kind()).isEqualTo(Kind.OPEN_LADDER);
    assertThat(List.of(ladder.valueAt(0), ladder.valueAt(3))).containsExactly(3.0, 12.0);
    assertThat(read("8 x 20 s").total()).isEqualTo(160);
    assertThat(read("8 x 20 s").measure()).isEqualTo(Measure.SECONDS);
    assertThat(read("5 x 5").values()).hasSize(5);
    assertThat(read("as many as you like").readable()).isFalse();
  }

  @Test
  @DisplayName("No text: the distance in metres, else the calories (women's for a woman), else the duration")
  void measures() {
    LinePrescription run =
        LinePrescription.of(
            WodMovement.builder().repsScheme("").distance(400.0).distanceUnit(Unit.METERS).build(), null);
    assertThat(run.measure()).isEqualTo(Measure.METERS);
    assertThat(run.total()).isEqualTo(400);

    WodMovement row = WodMovement.builder().calories(20).caloriesFemale(15).build();
    assertThat(LinePrescription.of(row, Division.WOMEN).total()).isEqualTo(15);
    assertThat(LinePrescription.of(row, Division.MEN).total()).isEqualTo(20);

    assertThat(LinePrescription.of(WodMovement.builder().build(), null).readable()).isFalse();
  }
}
