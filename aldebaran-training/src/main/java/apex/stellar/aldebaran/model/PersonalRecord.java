package apex.stellar.aldebaran.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Tracks personal records for movements and benchmark workouts */
@Entity
@Table(
    name = "personal_records",
    indexes = {
      @Index(name = "idx_user_movement", columnList = "userId, movementId"),
      @Index(name = "idx_user_workout", columnList = "userId, workoutId")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  // For movement PRs (1RM, max reps, etc.)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "movement_id")
  private Movement movement;

  // For benchmark workout PRs
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "workout_id")
  private Workout workout;

  @Enumerated(EnumType.STRING)
  private ScalingLevel scalingLevel;

  private LocalDate achievedDate;

  // Movement PR data
  private Double weight; // kg - for 1RM, etc.
  private Integer reps; // for max reps
  private Integer timeSeconds; // for max time holds

  // Benchmark PR data
  private Integer workoutTimeSeconds; // For time workouts
  private Integer rounds; // AMRAP
  private Integer totalReps; // AMRAP

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isCurrentPr = true;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
