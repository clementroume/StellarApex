package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import java.util.Set;

/**
 * Full DTO for Movement display.
 *
 * <p>Aggregates all movement properties, including internationalized content, anatomical analysis,
 * and load calculation logic.
 */
public record MovementResponse(
    // --- Identification ---
    Long id,
    String name,
    String nameAbbreviation,
    Category category,

    // --- Characteristics ---
    Set<Equipment> equipment,
    Set<Technique> techniques,
    Set<MovementMuscleResponse> targetedMuscles,

    // --- Internationalized Content ---
    String descriptionEn,
    String descriptionFr,
    String coachingCuesEn,
    String coachingCuesFr,

    // --- Media ---
    String videoUrl,
    String imageUrl) {}
