package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.model.enums.Unit.UnitType;
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
import jakarta.persistence.Transient;
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
 * <p>Renamed from WodPerformance to WodScore to reflect its primary role: storing the outcome.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wod_scores") // Index managed via Flyway
@EntityListeners(AuditingEntityListener.class)
public class WodScore {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  @NotNull
  private Long userId;

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
  // METRICS (Data)
  // -------------------------------------------------------------------------

  // --- Time (Canonical: Seconds) ---

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

  // --- Load (Weight) ---

  /** Heaviest weight lifted. Value stored in 'weightUnit'. */
  @Column(name = "max_weight")
  @DecimalMin("0.0")
  private Double maxWeight;

  /** Total tonnage. Value stored in 'weightUnit'. */
  @Column(name = "total_load")
  @DecimalMin("0.0")
  private Double totalLoad;

  /** The unit used for input/storage (KG, LBS...). */
  @Enumerated(EnumType.STRING)
  @Column(name = "weight_unit", length = 10)
  @Builder.Default
  private Unit weightUnit = Unit.KG;

  // --- Distance ---

  /** Total distance covered. Value stored in 'distanceUnit'. */
  @Column(name = "total_distance")
  @DecimalMin("0.0")
  private Double totalDistance;

  /** The unit used for input/storage (METERS, MILES...). */
  @Enumerated(EnumType.STRING)
  @Column(name = "distance_unit", length = 10)
  @Builder.Default
  private Unit distanceUnit = Unit.METERS;

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

  // -------------------------------------------------------------------------
  // UI HELPERS (Transient)
  // -------------------------------------------------------------------------

  @Transient
  public Integer getTimeMinutesPart() {
    return timeSeconds != null ? timeSeconds / 60 : null;
  }

  @Transient
  public Integer getTimeSecondsPart() {
    return timeSeconds != null ? timeSeconds % 60 : null;
  }

  // -------------------------------------------------------------------------
  // NORMALIZATION HELPERS (Transient - Critical for Leaderboards)
  // -------------------------------------------------------------------------

  /** Converts maxWeight to KG for ranking comparison. */
  @Transient
  public Double getMaxWeightInKg() {
    if (maxWeight == null || weightUnit == null) {
      return null;
    }
    return weightUnit.getType() == UnitType.MASS ? weightUnit.toBase(maxWeight) : null;
  }

  /** Converts totalLoad to KG for volume analysis. */
  @Transient
  public Double getTotalLoadInKg() {
    if (totalLoad == null || weightUnit == null) {
      return null;
    }
    return weightUnit.getType() == UnitType.MASS ? weightUnit.toBase(totalLoad) : null;
  }

  /** Converts totalDistance to Meters for ranking comparison. */
  @Transient
  public Double getTotalDistanceInMeters() {
    if (totalDistance == null || distanceUnit == null) {
      return null;
    }
    return distanceUnit.getType() == UnitType.DISTANCE ? distanceUnit.toBase(totalDistance) : null;
  }

  // ==================================================================================
  // INNER ENUM: SCALING
  // ==================================================================================

  /**
   * Represents the difficulty level at which a workout was performed relative to the prescription.
   */
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
