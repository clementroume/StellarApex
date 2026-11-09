package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.enums.Category;

/**
 * A movement that works a muscle.
 *
 * @param movementId The movement.
 * @param movementName Its name.
 * @param category Its category.
 * @param role The muscle's role.
 * @param trigger The technique or equipment that brings this role, or null for the movement itself.
 */
public record MuscleUsageResponse(
    Long movementId, String movementName, Category category, MuscleRole role, String trigger) {}
