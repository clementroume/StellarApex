package apex.stellar.aldebaran.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.service.UnitEquivalence.Implement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitEquivalenceTest {

  @Test
  @DisplayName("Barbell: the loads boxes use, both ways; the exact value rounded in competition")
  void barbell() {
    assertThat(UnitEquivalence.load(135.0, Unit.LBS, Unit.KG, Implement.BARBELL, false)).isEqualTo(60.0);
    assertThat(UnitEquivalence.load(95.0, Unit.LBS, Unit.KG, Implement.BARBELL, false)).isEqualTo(40.0);
    assertThat(UnitEquivalence.load(65.0, Unit.LBS, Unit.KG, Implement.BARBELL, false)).isEqualTo(30.0);
    assertThat(UnitEquivalence.load(60.0, Unit.KG, Unit.LBS, Implement.BARBELL, false)).isEqualTo(135.0);
    // From the training bar: 10 kg = 20 lb, the women's bar 15 kg = 35 lb
    assertThat(UnitEquivalence.load(10.0, Unit.KG, Unit.LBS, Implement.BARBELL, false)).isEqualTo(20.0);
    assertThat(UnitEquivalence.load(35.0, Unit.LBS, Unit.KG, Implement.BARBELL, false)).isEqualTo(15.0);
    // Not in the table: to the 2.5 kg / 5 lb
    assertThat(UnitEquivalence.load(145.0, Unit.LBS, Unit.KG, Implement.BARBELL, false)).isEqualTo(65.0);
    // Competition: the official tables (135 lb / 61 kg)
    assertThat(UnitEquivalence.load(135.0, Unit.LBS, Unit.KG, Implement.BARBELL, true)).isEqualTo(61.0);
    assertThat(UnitEquivalence.load(95.0, Unit.LBS, Unit.KG, Implement.BARBELL, true)).isEqualTo(43.0);
    // Same unit: unchanged
    assertThat(UnitEquivalence.load(43.0, Unit.KG, Unit.KG, Implement.BARBELL, false)).isEqualTo(43.0);
  }

  @Test
  @DisplayName("Kettlebells, dumbbells and medicine balls: the nearest real size (D155)")
  void implementSizes() {
    assertThat(UnitEquivalence.load(53.0, Unit.LBS, Unit.KG, Implement.KETTLEBELL, false)).isEqualTo(24.0);
    assertThat(UnitEquivalence.load(35.0, Unit.LBS, Unit.KG, Implement.KETTLEBELL, false)).isEqualTo(16.0);
    assertThat(UnitEquivalence.load(16.0, Unit.KG, Unit.LBS, Implement.KETTLEBELL, false)).isEqualTo(35.0);
    assertThat(UnitEquivalence.load(92.0, Unit.KG, Unit.LBS, Implement.KETTLEBELL, false)).isEqualTo(203.0);
    assertThat(UnitEquivalence.load(50.0, Unit.LBS, Unit.KG, Implement.DUMBBELL, false)).isEqualTo(22.5);
    assertThat(UnitEquivalence.load(22.5, Unit.KG, Unit.LBS, Implement.DUMBBELL, false)).isEqualTo(50.0);
    assertThat(UnitEquivalence.load(5.0, Unit.KG, Unit.LBS, Implement.DUMBBELL, false)).isEqualTo(10.0);
    assertThat(UnitEquivalence.load(20.0, Unit.LBS, Unit.KG, Implement.MEDBALL, false)).isEqualTo(9.0);
    assertThat(UnitEquivalence.load(14.0, Unit.LBS, Unit.KG, Implement.MEDBALL, false)).isEqualTo(6.0);
    assertThat(UnitEquivalence.load(9.0, Unit.KG, Unit.LBS, Implement.MEDBALL, false)).isEqualTo(20.0);
    assertThat(UnitEquivalence.sizes().dumbbellLb()).contains(2.5, 27.5, 30.0, 35.0, 125.0).doesNotContain(32.5);
    assertThat(UnitEquivalence.sizes().dumbbellKg()).hasSize(24);
  }

  @Test
  @DisplayName("Plyo boxes: three sizes, 20/24/30 in = 51/61/76 cm; other heights rounded")
  void heights() {
    assertThat(UnitEquivalence.height(24.0, Unit.INCHES, Unit.CENTIMETERS, true)).isEqualTo(61.0);
    assertThat(UnitEquivalence.height(20.0, Unit.INCHES, Unit.CENTIMETERS, true)).isEqualTo(51.0);
    assertThat(UnitEquivalence.height(60.0, Unit.CENTIMETERS, Unit.INCHES, true)).isEqualTo(24.0);
    assertThat(UnitEquivalence.height(75.0, Unit.CENTIMETERS, Unit.INCHES, true)).isEqualTo(30.0);
    // A wall ball target: 10 ft = 305 cm
    assertThat(UnitEquivalence.height(10.0, Unit.FEET, Unit.CENTIMETERS, false)).isEqualTo(305.0);
  }

  @Test
  @DisplayName("RX range: from the box value to the exact one, in the athlete's unit")
  void rxRange() {
    double[] kg = UnitEquivalence.rxRangeKg(135, Unit.LBS, Unit.KG, Implement.BARBELL, false);
    assertThat(kg[0]).isCloseTo(60, within(1e-9));
    assertThat(kg[1]).isCloseTo(61.235, within(1e-3));
    // An athlete loading in lb on a kg prescription: 60 kg or 135 lb
    double[] lb = UnitEquivalence.rxRangeKg(60, Unit.KG, Unit.LBS, Implement.BARBELL, false);
    assertThat(lb[0]).isCloseTo(60, within(1e-9));
    assertThat(lb[1]).isCloseTo(61.235, within(1e-3));
  }
}
