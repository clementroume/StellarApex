package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment.TriggerKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A technique or equipment that adds a muscle or changes its role.
 *
 * @param triggerKind Technique or equipment.
 * @param trigger The triggering value.
 * @param muscleId The muscle.
 * @param muscleName Its medical name (responses only).
 * @param role The role it takes.
 */
public record RoleAdjustmentDto(
    @NotNull TriggerKind triggerKind,
    @NotBlank String trigger,
    @NotNull Long muscleId,
    String muscleName,
    @NotNull MuscleRole role) {}
