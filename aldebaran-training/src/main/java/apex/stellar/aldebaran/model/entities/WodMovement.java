package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.exception.InvalidWodMovementException;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

/** Represents a prescribed movement within a WOD. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wod_movements")
public class WodMovement {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  @ToString.Exclude
  @NotNull
  private Wod wod;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  @NotNull
  @ToString.Exclude
  private Movement movement;

  @Column(name = "order_index", nullable = false)
  @Min(1)
  @NotNull
  private Integer orderIndex;

  /** Group of a weightlifting complex (lines of one group are done as one set), or null. */
  @Column(name = "complex_group")
  @Min(1)
  private Integer complexGroup;

  /** Part of the WOD the movement belongs to: buy-in, rounds (MAIN) or buy-out. */
  @Enumerated(EnumType.STRING)
  @Column(name = "segment", nullable = false, length = 10)
  @Builder.Default
  private Segment segment = Segment.MAIN;

  // --- Prescription ---
  /** Prescribed number of sets (strength / skill work), or null. */
  @Column(name = "sets")
  @Min(1)
  private Integer sets;

  @Column(name = "reps_scheme", length = 50)
  @Size(max = 50)
  private String repsScheme;

  @Column(name = "weight")
  @DecimalMin("0.0")
  private Double weight;

  /** Women's load, in the same unit as {@code weight} (which is the men's load). */
  @Column(name = "weight_female")
  @DecimalMin("0.0")
  private Double weightFemale;

  @Enumerated(EnumType.STRING)
  @Column(name = "weight_unit", length = 10)
  @Builder.Default
  private Unit weightUnit = Unit.KG;

  @Column(name = "duration_seconds")
  @Min(0)
  private Integer durationSeconds;

  @Enumerated(EnumType.STRING)
  @Column(name = "duration_display_unit", length = 10)
  @Builder.Default
  private Unit durationDisplayUnit = Unit.SECONDS;

  @Column(name = "distance")
  @DecimalMin("0.0")
  private Double distance;

  @Enumerated(EnumType.STRING)
  @Column(name = "distance_unit", length = 20)
  @Builder.Default
  private Unit distanceUnit = Unit.METERS;

  @Column(name = "calories")
  @Min(0)
  private Integer calories;

  /** Women's calories when they differ from the men's ({@code calories}). */
  @Column(name = "calories_female")
  @Min(0)
  private Integer caloriesFemale;

  /** Height of the box or the target for men, then women, in {@code heightUnit}. */
  @Column(name = "height")
  @DecimalMin("0.0")
  private Double height;

  @Column(name = "height_female")
  @DecimalMin("0.0")
  private Double heightFemale;

  @Enumerated(EnumType.STRING)
  @Column(name = "height_unit", length = 20)
  private Unit heightUnit;

  // --- Characteristics ---
  @ElementCollection
  @CollectionTable(
      name = "wod_movement_equipment",
      joinColumns = @JoinColumn(name = "wod_movement_id"))
  @Column(name = "equipment")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<String> equipment = new HashSet<>();

  @ElementCollection
  @CollectionTable(
      name = "wod_movement_techniques",
      joinColumns = @JoinColumn(name = "wod_movement_id"))
  @Column(name = "technique")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<String> techniques = new HashSet<>();

  // --- Instructions (EN / FR) ---
  @Column(name = "notes_en", columnDefinition = "TEXT")
  private String notesEn;

  @Column(name = "notes_fr", columnDefinition = "TEXT")
  private String notesFr;

  @Column(name = "scaling_options_en", columnDefinition = "TEXT")
  private String scalingOptionsEn;

  @Column(name = "scaling_options_fr", columnDefinition = "TEXT")
  private String scalingOptionsFr;

  // --- Business rules ---

  /**
   * Checks that this WOD movement only uses equipment and techniques allowed by its catalog
   * movement: a variant (e.g. Ring Dip = Dip + {@code RINGS}) must be declared on the movement.
   *
   * @param movementName The movement name, used in the error message.
   * @param allowedEquipment The equipment allowed by the movement.
   * @param allowedTechniques The techniques allowed by the movement.
   * @throws InvalidWodMovementException on the first value that is not allowed.
   */
  public void checkVariantsAllowed(
      String movementName, Set<Equipment> allowedEquipment, Set<Technique> allowedTechniques) {
    checkAllowed(equipment, allowedEquipment, "error.wod.movement.equipment.invalid", movementName);
    checkAllowed(
        techniques, allowedTechniques, "error.wod.movement.technique.invalid", movementName);
  }

  private static void checkAllowed(
      Set<String> values, Set<? extends Enum<?>> allowed, String messageKey, String movementName) {
    if (values == null || values.isEmpty()) {
      return;
    }
    Set<String> allowedNames =
        allowed == null
            ? Set.of()
            : allowed.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    for (String value : values) {
      if (!allowedNames.contains(value)) {
        throw new InvalidWodMovementException(messageKey, value, movementName);
      }
    }
  }

  // --- Equality and Hashing ---
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof WodMovement other)) {
      return false;
    }

    return getId() != null && getId().equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  /** Part of a WOD: before the rounds (buy-in), the rounds themselves, after them (buy-out). */
  public enum Segment {
    BUY_IN,
    MAIN,
    BUY_OUT
  }
}
