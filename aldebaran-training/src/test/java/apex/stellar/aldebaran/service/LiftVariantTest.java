package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.enums.Equipment;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiftVariantTest {

  private static Movement movement(Equipment... equipment) {
    return Movement.builder().id(1L).name("Lift").equipment(Set.of(equipment)).build();
  }

  @Test
  @DisplayName("Key: implied barbell (or only equipment) removed, the rest sorted")
  void key() {
    Movement deadlift = movement(Equipment.BARBELL, Equipment.TRAP_BAR);
    assertEquals(LiftVariant.BASE, LiftVariant.key(deadlift, Set.of("BARBELL"), Set.of()));
    assertEquals(LiftVariant.BASE, LiftVariant.key(deadlift, null, null));
    assertEquals("TRAP_BAR;", LiftVariant.key(deadlift, Set.of("TRAP_BAR"), Set.of()));
    assertEquals(";DEFICIT,PAUSED", LiftVariant.key(deadlift, Set.of(), Set.of("PAUSED", "DEFICIT")));

    Movement goblet = movement(Equipment.KETTLEBELL);
    assertEquals(LiftVariant.BASE, LiftVariant.key(goblet, Set.of("KETTLEBELL"), Set.of()));
    Movement split = movement(Equipment.KETTLEBELL, Equipment.DUMBBELL);
    assertEquals("DUMBBELL;", LiftVariant.key(split, Set.of("DUMBBELL"), Set.of()));
  }

  @Test
  @DisplayName("Key parts are read back")
  void parts() {
    assertEquals(Set.of("TRAP_BAR"), LiftVariant.equipmentOf("TRAP_BAR;PAUSED"));
    assertEquals(Set.of("PAUSED"), LiftVariant.techniquesOf("TRAP_BAR;PAUSED"));
    assertTrue(LiftVariant.equipmentOf("").isEmpty());
    assertTrue(LiftVariant.techniquesOf("TRAP_BAR;").isEmpty());
  }

  @Test
  @DisplayName("Epley from 2 to 10 reps only")
  void epley() {
    assertEquals(110.0, LiftVariant.epley(100, 3), 1e-9);
    assertEquals(100 * (1 + 10 / 30.0), LiftVariant.epley(100, 10), 1e-9);
    assertNull(LiftVariant.epley(100, 1));
    assertNull(LiftVariant.epley(100, 11));
  }

  @Test
  @DisplayName("A lift WOD: individual, scored by load, one main movement")
  void liftOf() {
    Movement m = movement(Equipment.BARBELL);
    assertTrue(LiftVariant.of(wod(ScoreType.WEIGHT, null, line(m, Segment.MAIN))).isPresent());
    assertTrue(LiftVariant.of(wod(ScoreType.NONE, null, line(m, Segment.MAIN))).isPresent());
    assertTrue(LiftVariant.of(wod(ScoreType.TIME, null, line(m, Segment.MAIN))).isEmpty());
    assertTrue(LiftVariant.of(wod(ScoreType.WEIGHT, 2, line(m, Segment.MAIN))).isEmpty());
    assertTrue(LiftVariant.of(wod(ScoreType.WEIGHT, null, line(m, Segment.BUY_IN))).isEmpty());
    assertTrue(
        LiftVariant.of(wod(ScoreType.WEIGHT, null, line(m, Segment.MAIN), line(m, Segment.MAIN)))
            .isEmpty());
  }

  private static WodMovement line(Movement m, Segment segment) {
    return WodMovement.builder().movement(m).orderIndex(1).segment(segment).build();
  }

  private static Wod wod(ScoreType type, Integer teamSize, WodMovement... lines) {
    return Wod.builder()
        .scoreType(type)
        .teamSize(teamSize)
        .movements(new ArrayList<>(List.of(lines)))
        .build();
  }
}
