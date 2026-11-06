package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.AgeGroup;
import apex.stellar.aldebaran.model.enums.Division;
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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An athlete's best load for a movement, a variant set and a number of reps (lot C, D47-D62).
 * Materialized from the scores by {@code MovementRecordService.rebuild}, never written by a request.
 */
@Entity
@Table(name = "movement_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  /**
   * Variant set ({@link apex.stellar.aldebaran.service.LiftVariant}): sorted equipment, {@code ;},
   * sorted techniques, implied equipment removed; empty for the base version.
   */
  @Column(name = "variant_key", nullable = false, length = 500)
  private String variantKey;

  @Column(nullable = false)
  private Integer reps;

  @Column(name = "weight_kg", nullable = false)
  private Double weightKg;

  /** Day of the score that set the record. */
  @Column(nullable = false)
  private LocalDate date;

  /** Score that set the record (its sets or its load). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "score_id", nullable = false)
  private Score score;

  /** Division copied from the score, for the box board filters. */
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Division division;

  /** Age group copied from the score, for the box board filters. */
  @Enumerated(EnumType.STRING)
  @Column(name = "age_group", length = 20)
  private AgeGroup ageGroup;
}
