package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import java.util.List;

/**
 * DTO representing anatomical muscle reference data.
 *
 * <p>{@code parent} is the functional group of the muscle (null for a group or a standalone
 * muscle); {@code members} lists the muscles of a group (empty otherwise).
 */
public record MuscleResponse(

    // --- Identification ---
    Long id,
    String medicalName,

    // --- Characteristics ---
    MuscleGroup muscleGroup,
    MuscleRefResponse parent,
    List<MuscleRefResponse> members,

    // --- Internationalized Content ---
    String commonNameEn,
    String commonNameFr,
    String descriptionEn,
    String descriptionFr,

    // --- Media ---
    String imageUrl) {}
