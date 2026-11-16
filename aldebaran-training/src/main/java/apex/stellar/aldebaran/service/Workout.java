package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.enums.Division;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One piece of work of an athlete, as the weekly balance reads it (D82): a score, or a session block
 * checked in a journal without a score (its prescription).
 *
 * @param wod The WOD, with its lines and movements.
 * @param score The score, or null for a checked block.
 * @param date The day it counts on (score date, else journal date).
 * @param blockKind Kind of the session block it belongs to, or null outside a session.
 * @param division The athlete's division (women's calories), or null.
 * @param teamMembers Members of the team that logged it, 0 when not a team score.
 */
public record Workout(
    Wod wod,
    @Nullable Score score,
    LocalDate date,
    @Nullable Kind blockKind,
    @Nullable Division division,
    int teamMembers) {}
