package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
 * A line of the WOD's official Scaled version (D141, D154): the variant and the quantities of the
 * line in that version, a value left null being the RX one. A prescription, kept in its unit of
 * origin like the RX line (D149).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wod_scaled_lines")
public class WodScaledLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_movement_id", nullable = false, unique = true)
  @ToString.Exclude
  private WodMovement wodMovement;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  @ToString.Exclude
  private Movement movement;

  @ElementCollection
  @CollectionTable(
      name = "wod_scaled_line_equipment",
      joinColumns = @JoinColumn(name = "wod_scaled_line_id"))
  @Column(name = "equipment")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<String> equipment = new HashSet<>();

  @ElementCollection
  @CollectionTable(
      name = "wod_scaled_line_techniques",
      joinColumns = @JoinColumn(name = "wod_scaled_line_id"))
  @Column(name = "technique")
  @BatchSize(size = 50)
  @Builder.Default
  private Set<String> techniques = new HashSet<>();

  @Column(name = "reps_scheme", length = 50)
  private String repsScheme;

  @Column(name = "weight")
  private Double weight;

  @Column(name = "weight_female")
  private Double weightFemale;

  @Enumerated(EnumType.STRING)
  @Column(name = "weight_unit", length = 10)
  private Unit weightUnit;

  @Column(name = "distance")
  private Double distance;

  @Enumerated(EnumType.STRING)
  @Column(name = "distance_unit", length = 20)
  private Unit distanceUnit;

  @Column(name = "calories")
  private Integer calories;

  @Column(name = "calories_female")
  private Integer caloriesFemale;

  @Column(name = "height")
  private Double height;

  @Column(name = "height_female")
  private Double heightFemale;

  @Enumerated(EnumType.STRING)
  @Column(name = "height_unit", length = 20)
  private Unit heightUnit;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WodScaledLine other)) {
      return false;
    }
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
