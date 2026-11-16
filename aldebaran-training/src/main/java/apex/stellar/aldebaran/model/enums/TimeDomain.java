package apex.stellar.aldebaran.model.enums;

/**
 * Time domain of a metcon (D86): continuous effort per crossfit.com's general guideline (very short
 * under 5 min, short 5 to 10, medium 11 to 20, long over 20), or intervals (EMOM, Tabata, work /
 * rest, a team taking turns).
 */
public enum TimeDomain {
  VERY_SHORT,
  SHORT,
  MEDIUM,
  LONG,
  INTERVALS;

  /**
   * The domain of a continuous effort.
   *
   * @param seconds Its duration.
   * @return Its domain.
   */
  public static TimeDomain ofContinuous(int seconds) {
    double minutes = seconds / 60.0;
    if (minutes < 5) {
      return VERY_SHORT;
    }
    if (minutes < 11) {
      return SHORT;
    }
    return minutes <= 20 ? MEDIUM : LONG;
  }
}
