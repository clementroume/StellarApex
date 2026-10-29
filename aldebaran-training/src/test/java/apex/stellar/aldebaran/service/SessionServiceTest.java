package apex.stellar.aldebaran.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.SessionBlock.Stimulus;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests of the session rules that need no repository. */
class SessionServiceTest {

  private static Wod wod(WodType type) {
    return Wod.builder().wodType(type).build();
  }

  @Test
  @DisplayName("deriveStimulus: from the block kind, its WOD format and the discipline")
  void deriveStimulus() {
    assertNull(SessionService.deriveStimulus(Kind.WARM_UP, null, Discipline.CROSSFIT));
    assertEquals(
        Stimulus.STRENGTH,
        SessionService.deriveStimulus(Kind.SKILL, wod(WodType.STRENGTH), Discipline.CROSSFIT));
    assertEquals(
        Stimulus.SKILL,
        SessionService.deriveStimulus(Kind.SKILL, wod(WodType.EMOM), Discipline.CROSSFIT));
    assertEquals(
        Stimulus.POWER,
        SessionService.deriveStimulus(
            Kind.SKILL, wod(WodType.STRENGTH), Discipline.WEIGHTLIFTING));
    assertEquals(
        Stimulus.MIXED,
        SessionService.deriveStimulus(Kind.WOD, wod(WodType.AMRAP), Discipline.CROSSFIT));
    assertEquals(
        Stimulus.STRENGTH,
        SessionService.deriveStimulus(Kind.ACCESSORY, wod(WodType.ACCESSORY), Discipline.CROSSFIT));
    assertEquals(
        Stimulus.THRESHOLD,
        SessionService.deriveStimulus(Kind.WOD, wod(WodType.INTERVALS), Discipline.ENDURANCE));
    assertEquals(
        Stimulus.AEROBIC,
        SessionService.deriveStimulus(Kind.WOD, wod(WodType.FOR_TIME), Discipline.ENDURANCE));
  }
}
