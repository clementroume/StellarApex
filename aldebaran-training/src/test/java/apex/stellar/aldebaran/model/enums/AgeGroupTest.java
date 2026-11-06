package apex.stellar.aldebaran.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgeGroupTest {

  @Test
  @DisplayName("Age = year of the score - birth year, CrossFit Open brackets")
  void brackets() {
    assertEquals(AgeGroup.AGE_15_AND_UNDER, AgeGroup.of(2012, 2026));
    assertEquals(AgeGroup.AGE_16_17, AgeGroup.of(2010, 2026));
    assertEquals(AgeGroup.AGE_18_34, AgeGroup.of(1992, 2026));
    assertEquals(AgeGroup.AGE_35_39, AgeGroup.of(1991, 2026));
    assertEquals(AgeGroup.AGE_60_64, AgeGroup.of(1962, 2026));
    assertEquals(AgeGroup.AGE_65_PLUS, AgeGroup.of(1950, 2026));
    assertNull(AgeGroup.of(null, 2026));
    assertNull(AgeGroup.of(2030, 2026));
  }

  @Test
  @DisplayName("Birth year header: blank or not a number is ignored")
  void parseBirthYear() {
    assertEquals(1990, AgeGroup.parseBirthYear(" 1990 "));
    assertNull(AgeGroup.parseBirthYear(""));
    assertNull(AgeGroup.parseBirthYear(null));
    assertNull(AgeGroup.parseBirthYear("abc"));
  }
}
