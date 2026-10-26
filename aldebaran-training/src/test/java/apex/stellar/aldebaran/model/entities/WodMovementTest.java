package apex.stellar.aldebaran.model.entities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import apex.stellar.aldebaran.exception.InvalidWodMovementException;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the variant rule of {@link WodMovement}. */
class WodMovementTest {

  private static final Set<Equipment> DIP_EQUIPMENT = Set.of(Equipment.RINGS, Equipment.BOX);
  private static final Set<Technique> DIP_TECHNIQUES = Set.of(Technique.STRICT, Technique.KIPPING);

  @Test
  @DisplayName("Accepts equipment and techniques declared on the movement")
  void shouldAcceptAllowedVariants() {
    WodMovement ringDip =
        WodMovement.builder().equipment(Set.of("RINGS")).techniques(Set.of("STRICT")).build();

    assertDoesNotThrow(() -> ringDip.checkVariantsAllowed("Dip", DIP_EQUIPMENT, DIP_TECHNIQUES));
  }

  @Test
  @DisplayName("Accepts a WOD movement without variants, even when the movement has none")
  void shouldAcceptNoVariants() {
    WodMovement plain = WodMovement.builder().equipment(null).techniques(Set.of()).build();

    assertDoesNotThrow(() -> plain.checkVariantsAllowed("Burpee", null, null));
  }

  @Test
  @DisplayName("Rejects an equipment that the movement does not allow")
  void shouldRejectEquipment() {
    WodMovement barbellDip = WodMovement.builder().equipment(Set.of("BARBELL")).build();

    InvalidWodMovementException ex =
        assertThrows(
            InvalidWodMovementException.class,
            () -> barbellDip.checkVariantsAllowed("Dip", DIP_EQUIPMENT, DIP_TECHNIQUES));

    assertEquals("error.wod.movement.equipment.invalid", ex.getMessageKey());
    assertArrayEquals(new Object[] {"BARBELL", "Dip"}, ex.getArgs());
  }

  @Test
  @DisplayName("Rejects a technique that the movement does not allow, or an unknown value")
  void shouldRejectTechnique() {
    WodMovement butterflyDip = WodMovement.builder().techniques(Set.of("BUTTERFLY")).build();
    WodMovement freeText = WodMovement.builder().techniques(Set.of("Ring dip")).build();

    assertEquals(
        "error.wod.movement.technique.invalid",
        assertThrows(
                InvalidWodMovementException.class,
                () -> butterflyDip.checkVariantsAllowed("Dip", DIP_EQUIPMENT, DIP_TECHNIQUES))
            .getMessageKey());
    assertThrows(
        InvalidWodMovementException.class,
        () -> freeText.checkVariantsAllowed("Dip", DIP_EQUIPMENT, DIP_TECHNIQUES));
  }
}
