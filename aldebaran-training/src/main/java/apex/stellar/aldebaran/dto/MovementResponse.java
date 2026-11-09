package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.entities.DumbbellBase;
import apex.stellar.aldebaran.model.entities.MovementJointAction;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.RequiredRule;
import apex.stellar.aldebaran.model.enums.Technique;
import java.util.List;
import java.util.Set;

/** Full DTO for Movement display. */
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
    String imageUrl,

    // --- Catalog model ---
    Equipment impliedEquipment,
    boolean impliedRequiresPosition,
    Set<RequiredRule> requiredRules,
    Set<DumbbellBase> dumbbellBases,
    List<MovementComponentDto> components,
    Set<AcceptedMovementResponse> acceptedMovements,
    Set<RoleAdjustmentDto> roleAdjustments,
    Set<MovementJointAction> jointActions,
    Set<UsualNameDto> usualNames) {

  /** A plain movement, without the catalog model. */
  @SuppressWarnings("java:S107") // Mirrors the record components
  public MovementResponse(
      Long id,
      String name,
      String nameAbbreviation,
      Category category,
      Set<Equipment> equipment,
      Set<Technique> techniques,
      Set<MovementMuscleResponse> targetedMuscles,
      String descriptionEn,
      String descriptionFr,
      String coachingCuesEn,
      String coachingCuesFr,
      String videoUrl,
      String imageUrl) {
    this(
        id, name, nameAbbreviation, category, equipment, techniques, targetedMuscles, descriptionEn,
        descriptionFr, coachingCuesEn, coachingCuesFr, videoUrl, imageUrl, null, false, Set.of(),
        Set.of(), List.of(), Set.of(), Set.of(), Set.of(), Set.of());
  }
}
