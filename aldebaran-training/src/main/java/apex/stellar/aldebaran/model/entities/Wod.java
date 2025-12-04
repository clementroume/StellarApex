package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.BenchmarkName;
import apex.stellar.aldebaran.model.enums.Modality;
import apex.stellar.aldebaran.model.enums.WorkoutType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "wods",
    indexes = {
      @Index(name = "idx_benchmark", columnList = "benchmarkName"),
      @Index(name = "idx_type", columnList = "type")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String title; // Ex: "Murph", "WOD 240520"

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkoutType type;

  @Enumerated(EnumType.STRING)
  private BenchmarkName benchmarkName; // Null si c'est un WOD du jour lambda

  // Métadonnées de création
  private Long creatorId; // Coach ou Admin

  @Builder.Default private boolean isPublic = false; // Visible par toute la box ?

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(columnDefinition = "TEXT")
  private String notes;

  // --- Structure Prescription ---
  private Integer timeCapSeconds;
  private Integer emomInterval;
  private Integer emomRounds;
  private String repScheme; // "21-15-9"

  // Tags pour analyse rapide sans parser les mouvements
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "wod_modalities", joinColumns = @JoinColumn(name = "wod_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "modality")
  @Builder.Default
  private Set<Modality> modalities = new HashSet<>();

  // La liste des ingrédients
  @OneToMany(mappedBy = "wod", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<WodMovement> movements = new ArrayList<>();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
