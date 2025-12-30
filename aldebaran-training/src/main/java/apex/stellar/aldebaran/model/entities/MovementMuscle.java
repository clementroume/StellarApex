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

/**
 * Represents the weighted biomechanical relationship between a {@link Movement} and a {@link
 * Muscle}.
 *
 * <p>This join entity allows for a nuanced analysis of exercise impact by defining the specific
 * {@link MuscleRole} (Agonist/Synergist) and an activation coefficient.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movement_muscles")
public class MovementMuscle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  @NotNull
  @JsonIgnore
  private Movement movement;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "muscle_id", nullable = false)
  @NotNull
  private Muscle muscle;

  /** The biomechanical function of the muscle in this specific movement context. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  @NotNull
  private MuscleRole role;

  /**
   * A coefficient (0.0 to 1.0) representing the degree of muscle activation. Used to weight the
   * training volume attribution.
   *
   * <p>Default is 1.0 (Full activation).
   */
  @Column(name = "impact_factor", nullable = false)
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @Builder.Default
  private Double impactFactor = 1.0;

  // ==================================================================================
  // INNER ENUM: MUSCLE ROLE
  // ==================================================================================

  /**
   * Defines the biomechanical role of a muscle during a specific movement.
   *
   * <p>In kinesiology, a muscle can act as a prime mover (Agonist), an assistant (Synergist), or a
   * stabilizer depending on the exercise. This distinction is crucial for calculating accurate
   * hypertrophy and fatigue metrics.
   */
  @Getter
  @RequiredArgsConstructor
  public enum MuscleRole {

    /**
     * The muscle that provides the primary force driving the movement. Also known as the "Prime
     * Mover".
     */
    AGONIST("Agonist"),

    /**
     * A muscle that assists the agonist in performing the movement. Often works at a mechanical
     * disadvantage or helps refine the trajectory.
     */
    SYNERGIST("Synergist"),

    /**
     * A muscle that contracts isometrically to stabilize a joint or body part. Allows the agonist
     * to work effectively.
     */
    STABILIZER("Stabilizer");

    /** A human-readable name suitable for UI display. */
    private final String displayName;
  }
}
