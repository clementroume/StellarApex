package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.Equipment;
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
import jakarta.persistence.Transient;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

/** Represents a standard exercise or movement definition in the catalog. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movements")
@EntityListeners(AuditingEntityListener.class)
public class Movement {

  /** Business key: {MODALITY_CODE}-{FAMILY_CODE}-{SEQUENCE}. */
  @Id
  @Column(length = 20, nullable = false)
  @NotBlank
  private String id;

  @Column(nullable = false, length = 50)
  @NotBlank
  private String name;

  @Column(name = "name_abbreviation", length = 20)
  @Size(max = 20)
  private String nameAbbreviation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @NotNull
  private Category category;

  // -------------------------------------------------------------------------
  // Requirements & Tags
  // -------------------------------------------------------------------------

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "movement_equipment", joinColumns = @JoinColumn(name = "movement_id"))
  @Column(name = "equipment", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Set<Equipment> equipment = new HashSet<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "movement_variations", joinColumns = @JoinColumn(name = "movement_id"))
  @Column(name = "technique", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Set<Technique> techniques = new HashSet<>();

  // -------------------------------------------------------------------------
  // Anatomy Engine
  // -------------------------------------------------------------------------

  @OneToMany(
      mappedBy = "movement",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<MovementMuscle> targetedMuscles = new HashSet<>();

  // -------------------------------------------------------------------------
  // Load Calculation Logic
  // -------------------------------------------------------------------------

  @Column(name = "involves_bodyweight", nullable = false)
  @Builder.Default
  private Boolean involvesBodyweight = false;

  @Column(name = "bodyweight_factor", nullable = false)
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  @Builder.Default
  private Double bodyweightFactor = 0.0;

  // -------------------------------------------------------------------------
  // Content & Media
  // -------------------------------------------------------------------------

  @Column(name = "description_en", columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(name = "description_fr", columnDefinition = "TEXT")
  private String descriptionFr;

  @Column(name = "coaching_cues_en", columnDefinition = "TEXT")
  private String coachingCuesEn;

  @Column(name = "coaching_cues_fr", columnDefinition = "TEXT")
  private String coachingCuesFr;

  @Column(name = "video_url", length = 512)
  @Size(max = 512)
  private String videoUrl;

  @Column(name = "image_url", length = 512)
  @Size(max = 512)
  private String imageUrl;

  // -------------------------------------------------------------------------
  // Audit
  // -------------------------------------------------------------------------

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // -------------------------------------------------------------------------
  // Helper Methods (Transient)
  // -------------------------------------------------------------------------

  /** Retrieves the Modality from the Family. Not stored in DB to avoid redundancy. */
  @Transient
  public Modality getModality() {
    return category != null ? category.getModality() : null;
  }

  /**
   * Checks if this movement typically involves tracking tonnage (Load * Reps). Essential for UI
   * logic (e.g., hiding the "Weight" field for Running).
   */
  @Transient
  public boolean isLoadBearing() {
    Modality mod = getModality();
    return mod != null && mod.isLoadBearing();
  }

  /**
   * Generates the semantic prefix for business IDs (e.g., "WL-SQ"). Used by services to generate
   * the full ID.
   */
  @Transient
  public String getSemanticIdPrefix() {
    return category != null ? category.semanticIdPrefix() : null;
  }

  // -------------------------------------------------------------------------
  // Equality based on business key
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Movement movement = (Movement) o;
    return id != null && id.equals(movement.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }
}
