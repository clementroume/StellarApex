package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One step of a chained movement (Clean and Jerk = Clean, then Jerk). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movement_components")
public class MovementComponent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "chain_id", nullable = false)
  private Movement chain;

  @Column(name = "component_order", nullable = false)
  private Integer componentOrder;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "component_id", nullable = false)
  private Movement component;

  /** Whether a performance of the chain also counts for the record of this component. */
  @Column(name = "counts_for_records", nullable = false)
  private boolean countsForRecords;

  /** Techniques the component always has in this chain (Cluster: Clean + SQUAT). */
  @Column(name = "techniques", nullable = false, length = 200)
  @Convert(converter = StringSetConverter.class)
  @Builder.Default
  private Set<String> techniques = new LinkedHashSet<>();

  /** Equipment the component always has in this chain, or empty to follow the line. */
  @Column(name = "equipment", nullable = false, length = 100)
  @Convert(converter = StringSetConverter.class)
  @Builder.Default
  private Set<String> equipment = new LinkedHashSet<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof MovementComponent other && id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
