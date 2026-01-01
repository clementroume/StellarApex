package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.validation.ValidScore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents the result/score of a workout performed by an athlete.
 *
 * <p><b>Storage Philosophy:</b> All metrics are stored in their canonical system units (SI) to
 * ensure comparability (Leaderboards, PRs) regardless of user input preferences.
 *
 * <ul>
 *   <li>Time: Seconds
 *   <li>Mass: Kilograms
 *   <li>Distance: Meters
 * </ul>
 *
 * <p>The {@code *DisplayUnit} fields store the user's preferred unit for rendering the value back.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wod_scores")
@EntityListeners(AuditingEntityListener.class)
@ValidScore
public class WodScore {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  @NotNull
  private String userId;

  @Column(nullable = false)
  @NotNull
  private LocalDate date;

  /** The WOD definition that was performed. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  @ToString.Exclude
  @NotNull
  private Wod wod;

  // -------------------------------------------------------------------------
  // METRICS (Normalized Storage)
  // -------------------------------------------------------------------------

  // --- Time (Ref: Seconds) ---

  /** Total time in seconds (Canonical storage). */
  @Column(name = "time_seconds")
  @Min(0)
  private Integer timeSeconds;

  /** Preferred display format (Minutes vs Seconds). */
  @Enumerated(EnumType.STRING)
  @Column(name = "time_display_unit", length = 10)
  @Builder.Default
  private Unit timeDisplayUnit = Unit.SECONDS;

  // --- Rounds & Reps ---

  @Column(name = "rounds")
  @Min(0)
  private Integer rounds;

  @Column(name = "reps")
  @Min(0)
  private Integer reps;

  // --- Load (Ref: KG) ---

  /** Heaviest weight lifted (Stored in KG). */
  @Column(name = "max_weight_kg")
  @DecimalMin("0.0")
  private Double maxWeightKg;

  /** Total tonnage (Stored in KG). */
  @Column(name = "total_load_kg")
  @DecimalMin("0.0")
  private Double totalLoadKg;

  /** The unit preferred by the user for display (e.g. LBS). */
  @Enumerated(EnumType.STRING)
  @Column(name = "weight_display_unit", length = 10)
  @Builder.Default
  private Unit weightDisplayUnit = Unit.KG;

  // --- Distance (Ref: Meters) ---

  /** Total distance covered (Stored in Meters). */
  @Column(name = "total_distance_meters")
  @DecimalMin("0.0")
  private Double totalDistanceMeters;

  /** The unit preferred by the user for display (e.g. MILES). */
  @Enumerated(EnumType.STRING)
  @Column(name = "distance_display_unit", length = 10)
  @Builder.Default
  private Unit distanceDisplayUnit = Unit.METERS;

  @Column(name = "total_calories")
  @Min(0)
  private Integer totalCalories;

  // -------------------------------------------------------------------------
  // STATUS & METADATA
  // -------------------------------------------------------------------------

  /** Is this the user's best score (PR) for this WOD? */
  @Column(name = "is_personal_record", nullable = false)
  @Builder.Default
  private boolean personalRecord = false;

  @Column(name = "time_capped", nullable = false)
  @Builder.Default
  private Boolean timeCapped = false;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @NotNull
  private ScalingLevel scaling;

  @Column(name = "scaling_notes", columnDefinition = "TEXT")
  private String scalingNotes;

  @Column(name = "user_comment", columnDefinition = "TEXT")
  private String userComment;

  @CreatedDate
  @Column(name = "logged_at", nullable = false, updatable = false)
  private LocalDateTime loggedAt;

  // ==================================================================================
  // INNER ENUM: SCALING
  // ==================================================================================

  /** Represents the difficulty level at which a workout was performed. */
  @Getter
  @RequiredArgsConstructor
  public enum ScalingLevel {
    RX("Rx"),
    SCALED("Scaled"),
    ELITE("Elite / Rx+"),
    CUSTOM("Custom / Modified");

    private final String displayName;
  }
}
