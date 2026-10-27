package apex.stellar.antares.dto;

/**
 * Type of a gym, for the internal API used by Aldebaran.
 *
 * @param programming Whether the gym is a virtual programming (no competition, no WOD copies).
 */
public record GymTypeResponse(boolean programming) {}
