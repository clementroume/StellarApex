package apex.stellar.aldebaran.model.enums;

import org.jspecify.annotations.Nullable;

/**
 * Age group of an athlete in the year of a score, with the CrossFit Open brackets (D55). Derived
 * from the birth year Antares sends ({@code X-Auth-User-Birth-Year}), which Aldebaran never stores:
 * only the group is copied on the score, like the division.
 */
public enum AgeGroup {
  AGE_15_AND_UNDER(0, 15),
  AGE_16_17(16, 17),
  AGE_18_34(18, 34),
  AGE_35_39(35, 39),
  AGE_40_44(40, 44),
  AGE_45_49(45, 49),
  AGE_50_54(50, 54),
  AGE_55_59(55, 59),
  AGE_60_64(60, 64),
  AGE_65_PLUS(65, Integer.MAX_VALUE);

  private final int min;
  private final int max;

  AgeGroup(int min, int max) {
    this.min = min;
    this.max = max;
  }

  /**
   * Lowest age of the group (used by the SQL classification of older scores).
   *
   * @return The age, in years.
   */
  public int min() {
    return min;
  }

  /**
   * Group of an athlete in a given year: age = year − birth year (the age reached that year).
   *
   * @param birthYear The birth year, or null when not given.
   * @param year The year of the score.
   * @return The group, or {@code null} without a birth year or for an impossible age.
   */
  public static @Nullable AgeGroup of(@Nullable Integer birthYear, int year) {
    if (birthYear == null) {
      return null;
    }
    int age = year - birthYear;
    if (age < 0) {
      return null;
    }
    for (AgeGroup group : values()) {
      if (age >= group.min && age <= group.max) {
        return group;
      }
    }
    return null;
  }

  /**
   * Parses a birth year header or Antares value.
   *
   * @param value A year, or anything else (blank, not a number).
   * @return The year, or {@code null} when not set or not a number.
   */
  public static @Nullable Integer parseBirthYear(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
