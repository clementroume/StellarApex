package apex.stellar.antares.dto;

/**
 * Answer of the internal membership check used by Aldebaran.
 *
 * @param member Whether the user holds a usable membership in the gym.
 */
public record GymMemberCheckResponse(boolean member) {}
