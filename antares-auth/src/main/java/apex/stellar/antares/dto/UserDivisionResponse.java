package apex.stellar.antares.dto;

import apex.stellar.antares.model.Division;

/**
 * Internal API answer: a user's competition division, nothing else.
 *
 * @param division The division, or null when not chosen.
 */
public record UserDivisionResponse(Division division) {}
