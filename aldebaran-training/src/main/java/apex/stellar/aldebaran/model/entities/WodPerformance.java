package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.ScalingLevel;
import apex.stellar.aldebaran.model.enums.ScoreType;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "performances",
    indexes = {
      @Index(name = "idx_user_date", columnList = "userId, date"),
      @Index(name = "idx_wod_user", columnList = "wod_id, userId")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WodPerformance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDate date;

  // Lien vers la définition du WOD
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "wod_id", nullable = false)
  private Wod wod;

  // --- Metrics & Score (Flattened) ---

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScoreType scoreType; // TIME, AMRAP, WEIGHT...

  // Metrics spécifiques selon le type de score
  private Integer timeSeconds; // Pour FOR TIME

  private Integer rounds; // Pour AMRAP
  private Integer reps; // Pour AMRAP (reps restantes) ou total reps simples

  private Double maxWeight; // Pour 1RM / Heavy Day (kg)
  private Double totalLoad; // Pour le tonnage total (kg)

  private Integer totalCalories; // Pour résultats Calorie
  private Double totalDistance; // Pour résultats Distance (mètres)

  // --- Metadata de l'exécution ---

  @Column(nullable = false)
  @Builder.Default
  private Boolean timeCapped = false; // A-t-on atteint le Time Cap ?

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScalingLevel scaling; // RX, SCALED...

  @Column(columnDefinition = "TEXT")
  private String scalingNotes; // "J'ai fait des Ring Rows au lieu des Pull-ups"

  @Column(columnDefinition = "TEXT")
  private String userComment; // "Sensations, douleur, stratégie..."

  @CreatedDate private LocalDateTime loggedAt;
}
