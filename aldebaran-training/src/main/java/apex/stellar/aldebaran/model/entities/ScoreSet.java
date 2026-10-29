package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One set of a strength score (D14): reps done at a load, in kilograms. A failed or partial set is
 * kept with {@code completed = false} and never counts as the performance.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ScoreSet {

  @Column(name = "reps", nullable = false)
  private Integer reps;

  @Column(name = "weight_kg")
  private Double weightKg;

  @Column(name = "completed", nullable = false)
  @Builder.Default
  private boolean completed = true;
}
