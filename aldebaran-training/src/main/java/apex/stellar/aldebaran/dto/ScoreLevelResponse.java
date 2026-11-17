package apex.stellar.aldebaran.dto;

/**
 * How a score being written compares with the prescription (lot S, D140-D142), for the score form to tell the athlete
 * before saving: as prescribed (RX possible), Rx+ (Scaled, every line at least RX and one harder), or following the
 * WOD's official Scaled version (ranked in Scaled).
 *
 * @param asPrescribed Every line as prescribed.
 * @param rxPlus No line lighter and one harder.
 * @param official Every line as the WOD's official Scaled version (false without a version).
 */
public record ScoreLevelResponse(boolean asPrescribed, boolean rxPlus, boolean official) {}
