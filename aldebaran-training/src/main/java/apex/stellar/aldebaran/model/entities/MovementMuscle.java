package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents the weighted biomechanical relationship between a {@link Movement} and a {@link
 * Muscle}.
 *
 * <p>This join entity allows for a nuanced analysis of exercise impact by defining the specific
 * {@link MuscleRole} (Agonist/Synergist) and an activation coefficient.
 */
@Entity
@Table(name = "movement_muscles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementMuscle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "muscle_id", nullable = false)
  private Muscle muscle;

  /** The biomechanical function of the muscle in this specific movement context. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MuscleRole role;

  /**
   * A coefficient (0.0 to 1.0) representing the degree of muscle activation. Used to weight the
   * training volume attribution.
   *
   * <p>Default is 1.0 (Full activation).
   */
  @Column(nullable = false)
  @Builder.Default
  private Double impactFactor = 1.0;

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
