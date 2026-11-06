package apex.stellar.antares.dto;

import apex.stellar.antares.model.Division;

/**
 * Internal API answer: the facts a score needs about its athlete (competition division and birth
 * year, from which Aldebaran derives the age group), nothing else.
 *
 * @param division The division, or null when not chosen.
 * @param birthYear The birth year, or null when not given.
 */
public record UserDivisionResponse(Division division, Integer birthYear) {}
