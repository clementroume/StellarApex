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
import jakarta.persistence.Transient;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents the definition of a Workout of the Day (The "Recipe").
 *
 * <p>This entity holds the structure and prescription of a workout, independently of any athlete's
 * result. It acts as the template for Performances.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "wods")
@EntityListeners(AuditingEntityListener.class)
public class Wod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // -------------------------------------------------------------------------
  // Identification & Metadata
  // -------------------------------------------------------------------------

  /** The title of the workout (e.g., "Murph", "WOD 240520"). */
  @Column(name = "title", nullable = false, length = 100)
  @NotBlank
  @Size(max = 100)
  private String title;

  /**
   * The structural or functional type of the workout. Acts as the discriminator for Benchmark
   * status.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "wod_type", nullable = false, length = 50)
  @NotNull
  private WodType wodType;

  /** The scoring logic defined for this WOD. Ex: "Fran" is always scored by TIME. */
  @Enumerated(EnumType.STRING)
  @Column(name = "score_type", nullable = false, length = 20)
  @NotNull
  private ScoreType scoreType;

  /** ID of the creator (Coach or Admin). Null for system/global WODs. */
  @Column(name = "creator_id")
  private Long creatorId;

  /** Visibility flag. If true, visible to the whole box/community. */
  @Column(name = "is_public", nullable = false)
  @Builder.Default
  private boolean isPublic = false;

  // -------------------------------------------------------------------------
  // Description & Content
  // -------------------------------------------------------------------------

  /** The full whiteboard description (e.g., "21-15-9 Thrusters..."). */
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  /** Coach's notes, stimulus explanation, or scaling guidelines. */
  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  // -------------------------------------------------------------------------
  // Structure & Prescription
  // -------------------------------------------------------------------------

  /** Maximum duration allowed for the workout in seconds. */
  @Column(name = "time_cap_seconds")
  @Min(0)
  private Integer timeCapSeconds;

  /** Interval duration for EMOM type workouts (typically 60s). */
  @Column(name = "emom_interval")
  @Min(0)
  private Integer emomInterval;

  /** Total number of rounds for EMOM type workouts. */
  @Column(name = "emom_rounds")
  @Min(0)
  private Integer emomRounds;

  /**
   * The repetition scheme string (e.g., "21-15-9", "5 rounds"). Useful for quick display/search
   * without parsing the full movement list.
   */
  @Column(name = "rep_scheme", length = 100)
  @Size(max = 100)
  private String repScheme;

  // -------------------------------------------------------------------------
  // Tags & Components
  // -------------------------------------------------------------------------

  /**
   * Tags for quick analysis (e.g., GYMNASTICS, WEIGHTLIFTING). Derived from the contained
   * movements.
   */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "wod_modalities", joinColumns = @JoinColumn(name = "wod_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "modality", length = 50, nullable = false)
  @Builder.Default
  private Set<Modality> modalities = new HashSet<>();

  /** The ordered list of movements that make up this WOD. */
  @OneToMany(mappedBy = "wod", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderIndex ASC")
  @Builder.Default
  private List<WodMovement> movements = new ArrayList<>();

  // -------------------------------------------------------------------------
  // Business Logic (Transient)
  // -------------------------------------------------------------------------
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  // -------------------------------------------------------------------------
  // Audit
  // -------------------------------------------------------------------------
  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // -------------------------------------------------------------------------
  // Helper Methods (Transient)
  // -------------------------------------------------------------------------

  /**
   * Determines if this WOD is considered a Benchmark based on its type.
   *
   * <p>A Benchmark is a reference workout used to test fitness and track progress. Includes "The
   * Girls", "Hero" WODs, and specific custom benchmarks.
   */
  @Transient
  public boolean isBenchmark() {
    return wodType == WodType.GIRLS
        || wodType == WodType.HERO
        || wodType == WodType.CUSTOM_BENCHMARK;
  }

  // -------------------------------------------------------------------------
  // Equality
  // -------------------------------------------------------------------------

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

  // ==================================================================================
  // INNER ENUM: WOD TYPE
  // ==================================================================================

  /** Defines the structural format or category of a WOD. */
  @Getter
  @RequiredArgsConstructor
  public enum WodType {
    // --- Time-based ---
    AMRAP("AMRAP"),
    EMOM("EMOM"),
    FOR_TIME("For Time"),
    TABATA("Tabata"),

    // --- Training Focus ---
    STRENGTH("Strength / Heavy Day"),
    SKILL("Skill / Technique"),
    ACCESSORY("Accessory"),

    // --- Special Formats ---
    CHIPPER("Chipper"),

    // --- Benchmarks ---
    GIRLS("The Girls"),
    HERO("Hero WOD"),
    CUSTOM_BENCHMARK("Custom Benchmark"),

    // --- Other ---
    CUSTOM("Custom");

    private final String displayName;
  }

  /** Defines the primary scoring metric for a performance. */
  @Getter
  @RequiredArgsConstructor
  public enum ScoreType {
    TIME("Time"),
    ROUNDS_REPS("Rounds + Reps"),
    REPS("Total Reps"),
    WEIGHT("Max Weight"),
    LOAD("Total Load"),
    CALORIES("Calories"),
    DISTANCE("Distance"),
    NONE("No Score");

    private final String displayName;
  }
}
