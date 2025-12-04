package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "performances",
    indexes = {
      @Index(name = "idx_user_date", columnList = "userId, date"),
      @Index(name = "idx_wod_user", columnList = "wod_id, userId")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WodPerformance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDate date;

  // Lien vers la définition du WOD
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  private Wod wod;

  // --- Metrics & Score (Flattened) ---

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScoreType scoreType;

  // Metrics spécifiques
  private Integer timeSeconds;
  private Integer rounds;
  private Integer reps;
  private Double maxWeight;
  private Double totalLoad;
  private Integer totalCalories;
  private Double totalDistance;

  // --- Metadata de l'exécution ---

  @Column(nullable = false)
  @Builder.Default
  private Boolean timeCapped = false;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScalingLevel scaling;

  @Column(columnDefinition = "TEXT")
  private String scalingNotes;

  @Column(columnDefinition = "TEXT")
  private String userComment;

  @CreatedDate private LocalDateTime loggedAt;

  /**
   * Represents the difficulty level at which a workout was performed relative to the prescription.
   */
  @Getter
  @RequiredArgsConstructor
  public enum ScalingLevel {
    /** Performed exactly as written. Only RX performances are eligible for Leaderboards/PRs. */
    RX("Rx"),
    /** Modified weights, movements, or reps to match athlete ability. */
    SCALED("Scaled"),
    /** Harder version than prescribed (e.g., vest). */
    ELITE("Elite / Rx+"),
    /** Significant modification changing the stimulus. */
    CUSTOM("Custom / Modified");

    private final String displayName;
  }

  /** Defines the primary scoring metric for a performance. */
  @Getter
  @RequiredArgsConstructor
  public enum ScoreType {
    TIME("Time"),
    ROUNDS_REPS("Rounds + Reps"),
    REPS("Total Reps"),
    WEIGHT("Max Weight"),
    LOAD("Total Load"),
    CALORIES("Calories"),
    DISTANCE("Distance"),
    NONE("No Score");

    private final String displayName;
  }
}
