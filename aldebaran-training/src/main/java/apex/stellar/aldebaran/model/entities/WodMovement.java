package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.persistence.Column;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a prescribed ingredient within a WOD (The "Recipe").
 *
 * <p>It defines <strong>WHAT</strong> to do (exercise, weight, reps) and in <strong>WHAT
 * ORDER</strong>. This entity links the generic {@link Wod} definition to the specific {@link
 * Movement}.
 *
 * <p><em>Note: This refers to the Prescription (Rx). Actual results are stored in
 * WodPerformance.</em>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wod_movements")
public class WodMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // -------------------------------------------------------------------------
  // Relationships
  // -------------------------------------------------------------------------

  /** The parent WOD definition. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  @ToString.Exclude
  @NotNull
  private Wod wod;

  /** The movement to be performed (The "Ingredient"). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  @NotNull
  @ToString.Exclude
  private Movement movement;

  /** Sequence order within the WOD (e.g., 1 for the first movement, 2 for the second). */
  @Column(name = "order_index", nullable = false)
  @Min(1)
  @NotNull
  private Integer orderIndex;

  // -------------------------------------------------------------------------
  // Prescription (Rx)
  // -------------------------------------------------------------------------

  /**
   * Textual representation of the rep scheme for this specific movement. Ex: "21-15-9", "5x5", "Max
   * Reps", "100m".
   */
  @Column(name = "reps_scheme", length = 50)
  @Size(max = 50)
  private String repsScheme;

  // --- Weight ---

  /** Prescribed weight value. Unit is defined by {@code weightUnit}. */
  @Column(name = "weight")
  @DecimalMin("0.0")
  private Double weight;

  /** The unit for the prescribed weight (Mass). Default: KG. */
  @Enumerated(EnumType.STRING)
  @Column(name = "weight_unit", length = 10)
  @Builder.Default
  private Unit weightUnit = Unit.KG;

  // --- Duration ---

  /** Target duration for static holds or cardio. */
  @Column(name = "duration_seconds")
  @Min(0)
  private Integer durationSeconds;

  /** The unit for the prescribed duration. Default: SECONDS. */
  @Enumerated(EnumType.STRING)
  @Column(name = "duration_display_unit", length = 10)
  @Builder.Default
  private Unit durationDisplayUnit = Unit.SECONDS;

  // --- Distance ---

  /** Target distance value. Unit is defined by {@code distanceUnit}. */
  @Column(name = "distance")
  @DecimalMin("0.0")
  private Double distance;

  /** The unit for the prescribed distance. Default: METERS. */
  @Enumerated(EnumType.STRING)
  @Column(name = "distance_unit", length = 10)
  @Builder.Default
  private Unit distanceUnit = Unit.METERS;

  // --- Calories ---

  /** Target energy expenditure (calories). */
  @Column(name = "calories")
  @Min(0)
  private Integer calories;

  // -------------------------------------------------------------------------
  // Equality
  // -------------------------------------------------------------------------

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

  // -------------------------------------------------------------------------
  // Metadata & Instructions
  // -------------------------------------------------------------------------

  /**
   * Specific instructions for this movement in this WOD context. Ex: "Touch and go", "Unbroken".
   */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  /**
   * Suggested scaling options displayed to the athlete. Ex: "Use PVC", "Knee push-ups", "Reduce
   * weight to 40kg".
   */
  @Column(name = "scaling_options", columnDefinition = "TEXT")
  private String scalingOptions;
}
