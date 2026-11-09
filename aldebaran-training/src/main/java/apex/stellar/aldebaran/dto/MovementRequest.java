package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.DumbbellBase;
import apex.stellar.aldebaran.model.entities.MovementJointAction;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.RequiredRule;
import apex.stellar.aldebaran.model.enums.Technique;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    @Size(max = 512, message = "{validation.url.size}") String imageUrl,

    // --- Catalog model ---
    Equipment impliedEquipment,
    boolean impliedRequiresPosition,
    Set<RequiredRule> requiredRules,
    Set<DumbbellBase> dumbbellBases,
    @Valid List<MovementComponentDto> components,
    Set<Long> acceptedMovementIds,
    @Valid Set<RoleAdjustmentDto> roleAdjustments,
    Set<MovementJointAction> jointActions,
    @Valid Set<UsualNameDto> usualNames) {

  /** A plain movement: no chain, no adjustment, no usual name. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public MovementRequest(
      String name,
      String nameAbbreviation,
      Category category,
      Set<Equipment> equipment,
      Set<Technique> techniques,
      Set<MovementMuscleRequest> muscles,
      String descriptionEn,
      String descriptionFr,
      String coachingCuesEn,
      String coachingCuesFr,
      String videoUrl,
      String imageUrl) {
    this(
        name, nameAbbreviation, category, equipment, techniques, muscles, descriptionEn,
        descriptionFr, coachingCuesEn, coachingCuesFr, videoUrl, imageUrl, null, false, null, null,
        null, null, null, null, null);
  }

  /**
   * Constructs a MovementRequest object with default values for equipment, techniques, and muscles
   * if null values are provided.
   */
  public MovementRequest {
    equipment = equipment != null ? equipment : new HashSet<>();
    techniques = techniques != null ? techniques : new HashSet<>();
    muscles = muscles != null ? muscles : new HashSet<>();
    requiredRules = requiredRules != null ? requiredRules : new HashSet<>();
    dumbbellBases = dumbbellBases != null ? dumbbellBases : new HashSet<>();
    components = components != null ? components : new ArrayList<>();
    acceptedMovementIds = acceptedMovementIds != null ? acceptedMovementIds : new HashSet<>();
    roleAdjustments = roleAdjustments != null ? roleAdjustments : new HashSet<>();
    jointActions = jointActions != null ? jointActions : new HashSet<>();
    usualNames = usualNames != null ? usualNames : new HashSet<>();
  }
}
