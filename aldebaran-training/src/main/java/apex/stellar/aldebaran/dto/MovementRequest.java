package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

/** DTO for creating or updating a Movement in the catalog. */
public record MovementRequest(
    // --- Identification ---
    @NotBlank(message = "{validation.movement.name.required}")
        @Size(max = 50, message = "{validation.movement.name.size}")
        String name,
    @Size(max = 20, message = "{validation.movement.abbreviation.size}") String nameAbbreviation,
    @NotNull(message = "{validation.movement.category.required}") Category category,

    // --- Characteristics ---
    @NotNull Set<Equipment> equipment,
    @NotNull Set<Technique> techniques,
    @Valid Set<MovementMuscleRequest> muscles,

    // --- Internationalized Content ---
    @Size(max = 4000, message = "{validation.movement.description.size}") String descriptionEn,
    @Size(max = 4000, message = "{validation.movement.description.size}") String descriptionFr,
    @Size(max = 4000, message = "{validation.movement.description.size}") String coachingCuesEn,
    @Size(max = 4000, message = "{validation.movement.description.size}") String coachingCuesFr,

    // --- Media ---
    @Size(max = 512, message = "{validation.url.size}") String videoUrl,
    @Size(max = 512, message = "{validation.url.size}") String imageUrl) {

  /**
   * Constructs a MovementRequest object with default values for equipment, techniques, and muscles
   * if null values are provided.
   */
  public MovementRequest {
    equipment = equipment != null ? equipment : new HashSet<>();
    techniques = techniques != null ? techniques : new HashSet<>();
    muscles = muscles != null ? muscles : new HashSet<>();
  }
}
