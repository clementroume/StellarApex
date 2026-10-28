package apex.stellar.aldebaran.model.enums;

import org.jspecify.annotations.Nullable;

/**
 * Competition division of an athlete (CrossFit Open style), chosen in the Antares profile and sent
 * as {@code X-Auth-User-Division}. Decides which load of a men/women pair applies and splits
 * leaderboards.
 */
public enum Division {
  MEN,
  WOMEN;

  /**
   * Parses a header or Antares value.
   *
   * @param value {@code MEN}, {@code WOMEN}, or anything else (blank, unknown).
   * @return The division, or {@code null} when not set or not recognized.
   */
  public static @Nullable Division parse(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Division.valueOf(value.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
