package apex.stellar.aldebaran.model.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Represents the weighted biomechanical relationship between a Movement and a Muscle. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movement_muscles")
public class MovementMuscle {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // --- Relationships ---
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  @NotNull
  @JsonIgnore
  @ToString.Exclude
  private Movement movement;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "muscle_id", nullable = false)
  @NotNull
  private Muscle muscle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  @NotNull
  private MuscleRole role;

  @Column(name = "impact_factor", nullable = false)
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @Builder.Default
  private Double impactFactor = 1.0;

  // --- Equality and Hashing ---
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof MovementMuscle other)) {
      return false;
    }

    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  /** MuscleRole Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum MuscleRole {
    AGONIST,
    SYNERGIST,
    STABILIZER;
  }
}
