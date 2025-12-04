package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a movement within a workout with its prescribed sets/reps. For EMOM: could represent
 * what happens on even/odd minutes For Chipper: represents each movement in sequence
 */
@Entity
@Table(name = "workout_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WodMovement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Relation vers la définition du WOD (La Recette)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  private Wod wod;

  // Lien vers la bibliothèque de mouvements
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  @Column(nullable = false)
  private Integer orderIndex; // 1, 2, 3... pour l'ordre d'affichage

  // --- Prescription (Rx) ---

  // Ex: "21-15-9" ou "5x5" ou "Max reps"
  private String targetRepsScheme;

  // Poids prescrit (Rx)
  private Double rxWeight; // kg (Renommé de prescribedWeight pour plus de clarté "CrossFit")
  private String rxWeightNote; // "Bodyweight", "1.5x BW", "Heavy"

  // Objectifs pour les mouvements cardio ou statiques
  private Integer targetDurationSeconds; // Ex: Plank hold
  private Double targetDistance; // meters
  private Integer targetCalories;

  // --- Metadata ---

  @Column(columnDefinition = "TEXT")
  private String notes; // Instructions spécifiques (ex: "Touch and go")

  @Column(columnDefinition = "TEXT")
  private String scalingOptions; // Suggestions : "Use PVC", "Knee push-ups"
}
