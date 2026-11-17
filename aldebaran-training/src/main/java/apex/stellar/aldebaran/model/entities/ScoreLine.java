package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

/**
 * A line of the WOD done otherwise than prescribed (D142): the variant done and the quantities the
 * line prescribes; a value left null is as prescribed. Loads are in kg and distances or heights in
 * meters (performances, base unit). On a "max" line of a WOD with several, {@code repsScheme} holds
 * the reps counted, not a change (D147).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "score_lines")
public class ScoreLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "score_id", nullable = false)
  @ToString.Exclude
  private Score score;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_movement_id", nullable = false)
  @ToString.Exclude
  private WodMovement wodMovement;

  /** Movement done (the prescribed one when only a quantity or the variant changed). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  @ToString.Exclude
  private Movement movement;

  @ElementCollection
  @CollectionTable(name = "score_line_equipment", joinColumns = @JoinColumn(name = "score_line_id"))
  @Column(name = "equipment")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<String> equipment = new HashSet<>();

  @ElementCollection
  @CollectionTable(
      name = "score_line_techniques",
      joinColumns = @JoinColumn(name = "score_line_id"))
  @Column(name = "technique")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<String> techniques = new HashSet<>();

  @Column(name = "reps_scheme", length = 50)
  private String repsScheme;

  @Column(name = "weight_kg")
  private Double weightKg;

  @Column(name = "distance_meters")
  private Double distanceMeters;

  @Column(name = "calories")
  private Integer calories;

  @Column(name = "height_meters")
  private Double heightMeters;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ScoreLine other)) {
      return false;
    }
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
