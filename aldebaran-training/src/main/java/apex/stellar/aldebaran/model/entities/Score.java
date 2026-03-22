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

/** Represents the result/score of a workout performed by an athlete. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "scores")
@EntityListeners(AuditingEntityListener.class)
@ValidScore
public class Score {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  @NotNull
  private Long userId;

  @Column(nullable = false)
  @NotNull
  private LocalDate date;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  @ToString.Exclude
  @NotNull
  private Wod wod;

  // --- Time Metrics ---
  @Column(name = "time_seconds")
  @Min(0)
  private Integer timeSeconds;

  @Enumerated(EnumType.STRING)
  @Column(name = "time_display_unit", length = 10)
  @Builder.Default
  private Unit timeDisplayUnit = Unit.SECONDS;

  // --- Volume Metrics ---
  @Column(name = "rounds")
  @Min(0)
  private Integer rounds;

  @Column(name = "reps")
  @Min(0)
  private Integer reps;

  // --- Load Metrics ---
  @Column(name = "max_weight_kg")
  @DecimalMin("0.0")
  private Double maxWeightKg;

  @Column(name = "total_load_kg")
  @DecimalMin("0.0")
  private Double totalLoadKg;

  @Enumerated(EnumType.STRING)
  @Column(name = "weight_display_unit", length = 10)
  @Builder.Default
  private Unit weightDisplayUnit = Unit.KG;

  // --- Distance Metrics ---
  @Column(name = "total_distance_meters")
  @DecimalMin("0.0")
  private Double totalDistanceMeters;

  @Enumerated(EnumType.STRING)
  @Column(name = "distance_display_unit", length = 10)
  @Builder.Default
  private Unit distanceDisplayUnit = Unit.METERS;

  // --- Calories ---
  @Column(name = "total_calories")
  @Min(0)
  private Integer totalCalories;

  // --- Performance Context ---
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @NotNull
  private ScalingLevel scaling;

  @Column(name = "time_capped", nullable = false)
  @Builder.Default
  private Boolean timeCapped = false;

  @Column(name = "is_personal_record", nullable = false)
  @Builder.Default
  private boolean personalRecord = false;

  // --- Comments and Scaling notes---
  @Column(name = "user_comment", columnDefinition = "TEXT")
  private String userComment;

  @Column(name = "scaling_notes", columnDefinition = "TEXT")
  private String scalingNotes;

  // --- Audit ---
  @CreatedDate
  @Column(name = "logged_at", nullable = false, updatable = false)
  private LocalDateTime loggedAt;

  // --- Equality and Hashing ---
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Score score = (Score) o;
    return id != null && id.equals(score.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  /** ScalingLevel Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum ScalingLevel {
    RX,
    SCALED,
    ELITE,
    CUSTOM
  }
}
