package apex.stellar.aldebaran.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * Represents a movement within a workout with its prescribed sets/reps. For EMOM: could represent
 * what happens on even/odd minutes For Chipper: represents each movement in sequence
 */
@Entity
@Table(name = "workout_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workout_id", nullable = false)
  private Workout workout;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  @Column(nullable = false)
  private Integer orderIndex;

  // For prescriptions like "21-15-9" or "5x5"
  private String targetRepsScheme;

  // For prescribed weights
  private Double prescribedWeight; // kg
  private String prescribedWeightNote; // "Bodyweight", "1.5x BW", etc.

  // For timed movements (Row 500m, Run 400m)
  private Integer targetDurationSeconds;
  private Double targetDistance; // meters
  private Integer targetCalories;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "workout_movement_sets",
      joinColumns = @JoinColumn(name = "workout_movement_id"))
  @OrderColumn(name = "set_order")
  @Builder.Default
  private List<MovementSet> sets = new ArrayList<>();

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(columnDefinition = "TEXT")
  private String scalingOptions; // Alternative movements for scaling
}
