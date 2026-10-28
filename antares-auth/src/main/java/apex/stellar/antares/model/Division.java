package apex.stellar.antares.model;

/**
 * Competition division chosen in the athlete profile (CrossFit Open style). Decides which load of a
 * men/women pair Sirius highlights and splits leaderboards (sent to Aldebaran as {@code
 * X-Auth-User-Division}).
 */
public enum Division {
  MEN,
  WOMEN
}
