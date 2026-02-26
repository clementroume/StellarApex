package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/**
 * Full DTO for Movement display.
 *
 * <p>Aggregates all movement properties, including internationalized content, anatomical analysis,
 * and load calculation logic.
 */
public record MovementResponse(
    // --- Identification ---
    @Schema(description = "Unique Business ID", example = "WL-SQ-001") String id,
    @Schema(description = "Display name", example = "Back Squat") String name,
    @Schema(description = "Short abbreviation", example = "BS") String nameAbbreviation,
    @Schema(description = "Functional category") Category category,

    // --- Characteristics ---
    @Schema(description = "Required equipment") Set<Equipment> equipment,
    @Schema(description = "Technique variations") Set<Technique> techniques,
    @Schema(description = "Anatomical muscle analysis with activation roles")
        List<MovementMuscleResponse> targetedMuscles,

    // --- Load Logic ---
    @Schema(description = "Whether body weight is factored into the load")
        boolean involvesBodyweight,
    @Schema(description = "Bodyweight factor coefficient (0.0 - 1.0)") Double bodyweightFactor,
    @Schema(
            description = "Indicates if this movement typically requires an external load (weight)",
            example = "true")
        boolean loadBearing,

    // --- Internationalized Content ---
    @Schema(description = "Description in English") String descriptionEn,
    @Schema(description = "Description en Français") String descriptionFr,
    @Schema(description = "Coaching cues in English") String coachingCuesEn,
    @Schema(description = "Conseils de coaching en Français") String coachingCuesFr,

    // --- Media ---
    @Schema(description = "Demo video URL") String videoUrl,
    @Schema(description = "Anatomical image URL") String imageUrl) {}
