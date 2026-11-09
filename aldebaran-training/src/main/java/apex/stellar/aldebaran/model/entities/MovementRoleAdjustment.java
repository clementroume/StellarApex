package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A technique or an equipment adds a muscle to a movement or changes its role. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movement_role_adjustments")
public class MovementRoleAdjustment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_kind", nullable = false, length = 10)
  private TriggerKind triggerKind;

  @Column(name = "trigger", nullable = false, length = 50)
  private String trigger;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "muscle_id", nullable = false)
  private Muscle muscle;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 50)
  private MuscleRole role;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof MovementRoleAdjustment other && id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  /** What triggers an adjustment. */
  public enum TriggerKind {
    TECHNIQUE,
    EQUIPMENT
  }
}
