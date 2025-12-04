package apex.stellar.aldebaran.model;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "movement_prime_movers",
      joinColumns = @JoinColumn(name = "movement_id"),
      inverseJoinColumns = @JoinColumn(name = "muscle_id"))
  @Builder.Default
  private Set<Muscle> agonists = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "movement_synergists",
      joinColumns = @JoinColumn(name = "movement_id"),
      inverseJoinColumns = @JoinColumn(name = "muscle_id"))
  @Builder.Default
  private Set<Muscle> synergists = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "movement_stabilizers",
      joinColumns = @JoinColumn(name = "movement_id"),
      inverseJoinColumns = @JoinColumn(name = "muscle_id"))
  @Builder.Default
  private Set<Muscle> stabilizers = new HashSet<>();

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
