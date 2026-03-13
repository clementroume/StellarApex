package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Category.Modality;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Represents the definition of a Workout of the Day (WOD). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wods")
@EntityListeners(AuditingEntityListener.class)
public class Wod {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(name = "version")
  private Long version;

  @Column(name = "title", nullable = false, length = 100)
  @NotBlank
  @Size(max = 100)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "wod_type", nullable = false, length = 50)
  @NotNull
  private WodType wodType;

  @Enumerated(EnumType.STRING)
  @Column(name = "score_type", nullable = false, length = 20)
  @NotNull
  private ScoreType scoreType;

  // --- Authorship and Visibility ---
  @Column(name = "author_id")
  private Long authorId;

  @Column(name = "gym_id")
  private Long gymId;

  @Column(name = "is_public", nullable = false)
  @Builder.Default
  private boolean isPublic = false;

  // --- Description and Notes ---
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  // --- Structure and Prescription ---
  @Column(name = "time_cap_seconds")
  @Min(0)
  private Integer timeCapSeconds;

  @Column(name = "emom_interval")
  @Min(0)
  private Integer emomInterval;

  @Column(name = "emom_rounds")
  @Min(0)
  private Integer emomRounds;

  @Column(name = "rep_scheme", length = 100)
  @Size(max = 100)
  private String repScheme;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "wod_modalities", joinColumns = @JoinColumn(name = "wod_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "modality", length = 50, nullable = false)
  @Builder.Default
  private Set<Modality> modalities = new HashSet<>();

  @OneToMany(mappedBy = "wod", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @Builder.Default
  @ToString.Exclude
  private List<WodMovement> movements = new ArrayList<>();

  // --- Audit ---
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // --- Equality and Hashing ---
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Wod wod = (Wod) o;
    return id != null && id.equals(wod.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }

  /** WodType Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum WodType {
    // --- Time-based ---
    AMRAP,
    EMOM,
    FOR_TIME,
    TABATA,
    // --- Training Focus ---
    STRENGTH,
    SKILL,
    ACCESSORY,
    // --- Special Formats ---
    CHIPPER,
    // --- Benchmarks ---
    GIRLS,
    HERO,
    CUSTOM_BENCHMARK,
    // --- Other ---
    CUSTOM;
  }

  /** ScoreType Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum ScoreType {
    TIME,
    ROUNDS_REPS,
    REPS,
    WEIGHT,
    LOAD,
    CALORIES,
    DISTANCE,
    NONE;
  }
}
