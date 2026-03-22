package apex.stellar.aldebaran.model.entities;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

  // --- Prescription ---
  @Column(name = "reps_scheme", length = 50)
  @Size(max = 50)
  private String repsScheme;

  @Column(name = "weight")
  @DecimalMin("0.0")
  private Double weight;

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
  @Column(name = "distance_unit", length = 10)
  @Builder.Default
  private Unit distanceUnit = Unit.METERS;

  @Column(name = "calories")
  @Min(0)
  private Integer calories;

  // --- Characteristics ---
  @ElementCollection
  @CollectionTable(
      name = "wod_movement_equipment",
      joinColumns = @JoinColumn(name = "wod_movement_id"))
  @Column(name = "equipment")
  @Builder.Default
  private Set<String> equipment = new HashSet<>();

  @ElementCollection
  @CollectionTable(
      name = "wod_movement_techniques",
      joinColumns = @JoinColumn(name = "wod_movement_id"))
  @Column(name = "technique")
  @Builder.Default
  private Set<String> techniques = new HashSet<>();

  // --- Instructions ---
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "scaling_options", columnDefinition = "TEXT")
  private String scalingOptions;

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
}
