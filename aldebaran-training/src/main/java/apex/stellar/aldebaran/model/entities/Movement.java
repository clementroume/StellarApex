package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Family;
import apex.stellar.aldebaran.model.enums.Modality;
import apex.stellar.aldebaran.model.enums.Technique;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "movements")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movement {
  @Id
  @Column(length = 20)
  private String id; // M-CLEAN-001, G-PULLUP-001, C-ROW-001

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Modality modality;

  @Column(nullable = false)
  private String name;

  private String nameAbbreviation; // For scoreboards: C&J, HSPU, T2B, etc.

  private Family family; // Olympic lifts, squats, presses, etc.

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "movement_equipment", joinColumns = @JoinColumn(name = "movement_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "equipment")
  @Builder.Default
  private Set<Equipment> equipment = new HashSet<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "movement_variations", joinColumns = @JoinColumn(name = "movement_id"))
  @Column(name = "variation")
  @Builder.Default
  private Set<Technique> techniques = new HashSet<>(); // Strict, kipping, butterfly, etc.

  @OneToMany(
      mappedBy = "movement",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @Builder.Default
  private Set<MovementMuscle> affectedMuscles = new HashSet<>();

  @Column(nullable = false)
  @Builder.Default
  private Boolean involvesBodyweight = false;

  // Facteur de charge corporelle (1.0 pour Pull-up, 0.65 pour Push-up, 0.0 pour Bench Press)
  // Charge Totale = (Poids Barre + (Poids Athlète * bodyweightFactor)) * Reps
  @Builder.Default private Double bodyweightFactor = 0.0;

  @Column(columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(columnDefinition = "TEXT")
  private String descriptionFr;

  @Column(columnDefinition = "TEXT")
  private String coachingCuesEn;

  @Column(columnDefinition = "TEXT")
  private String coachingCuesFr;

  private String videoUrl;

  @Builder.Default
  @Column(nullable = false)
  private Boolean isActive = true;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
