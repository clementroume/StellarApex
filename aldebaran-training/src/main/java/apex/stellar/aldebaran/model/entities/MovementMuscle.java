package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.emuns.MuscleRole;
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

@Entity
@Table(name = "movement_muscles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementMuscle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "muscle_id", nullable = false)
  private Muscle muscle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MuscleRole role; // AGONIST, SYNERGIST, STABILIZER

  // LE POINT CLÉ : Coefficient d'activation (0.0 à 1.0)
  // Ex: Pour un Squat -> Quads (1.0), Glutes (0.7), Calves (0.3)
  @Column(nullable = false)
  @Builder.Default
  private Double impactFactor = 1.0;
}
