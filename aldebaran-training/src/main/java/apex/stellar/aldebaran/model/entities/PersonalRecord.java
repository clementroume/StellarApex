package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tracks personal records. This is a DERIVED entity (Read Model). The Source of Truth is the
 * Performance entity.
 */
@Entity
@Table(
    name = "personal_records",
    indexes = {
      @Index(name = "idx_user_movement", columnList = "userId, movement_id"),
      @Index(
          name = "idx_user_wod",
          columnList = "userId, wod_id") // Correction: workoutId -> wod_id
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  // --- Source of Truth Link ---
  // Permet de remonter à la séance exacte qui a généré ce PR.
  // Indispensable pour l'audit ou si on supprime la performance source.
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_performance_id", nullable = false)
  private WodPerformance sourcePerformance;

  // --- Context (Mutually Exclusive) ---

  // Cas 1 : PR sur un Mouvement (ex: 1RM Clean)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "movement_id")
  private Movement movement;

  // Cas 2 : PR sur un Benchmark WOD (ex: Fran)
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "wod_id")
  private Wod benchmarkWod;

  // --- Result Data ---
  
  private LocalDate achievedDate;

  // Metrics (Flattened for easier querying/sorting)
  // On pourrait utiliser un @Embedded, mais à plat c'est souvent plus simple pour les MAX() SQL

  // Mouvement
  private Double weight; // kg
  private Integer reps; // max reps
  private Integer timeSeconds; // max hold

  // WOD
  private Integer workoutTimeSeconds;
  private Integer rounds;
  private Integer totalReps;

  @Column(columnDefinition = "TEXT")
  private String notes;

  @Column(nullable = false)
  @Builder.Default
  private Boolean isCurrentPr = true; // Flag pour accès rapide au record actuel

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
