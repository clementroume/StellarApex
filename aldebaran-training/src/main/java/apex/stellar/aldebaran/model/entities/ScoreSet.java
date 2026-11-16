package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One strength set of a score, done on one line of the WOD (D143). A set of a complex is one round,
 * linked to the complex's first line.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "score_sets")
public class ScoreSet {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "score_id", nullable = false)
  @ToString.Exclude
  private Score score;

  /** Position of the set within the score, from 0. */
  @Column(name = "set_index", nullable = false)
  private int setIndex;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_movement_id", nullable = false)
  @ToString.Exclude
  private WodMovement wodMovement;

  @Column(name = "reps", nullable = false)
  private Integer reps;

  @Column(name = "weight_kg")
  private Double weightKg;

  @Column(name = "completed", nullable = false)
  @Builder.Default
  private boolean completed = true;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ScoreSet other)) {
      return false;
    }
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
