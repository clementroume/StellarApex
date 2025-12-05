package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Represents a specific anatomical muscle in the human body.
 *
 * <p>This entity serves as the granular unit for biomechanical analysis. Muscles are categorized by
 * {@link MuscleGroup} to facilitate high-level reporting and visualization (e.g., "Upper Body" vs.
 * "Lower Body" volume).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "muscles")
public class Muscle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The Latin/Medical name (e.g., "Pectoralis Major"). Acts as the business key. */
  @Column(name = "medical_name", unique = true, nullable = false, length = 100)
  @NotBlank
  private String medicalName;

  @Column(name = "common_name_en", length = 100)
  private String commonNameEn;

  @Column(name = "common_name_fr", length = 100)
  private String commonNameFr;

  @Column(name = "description_en", columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(name = "description_fr", columnDefinition = "TEXT")
  private String descriptionFr;

  /** The major anatomical group this muscle belongs to. */
  @Enumerated(EnumType.STRING)
  @Column(name = "muscle_group", nullable = false, length = 50)
  @NotNull
  private MuscleGroup muscleGroup;

  // -------------------------------------------------------------------------
  // Equality based on business key (medicalName)
  // -------------------------------------------------------------------------
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Muscle muscle = (Muscle) o;
    return medicalName != null && medicalName.equals(muscle.medicalName);
  }

  @Override
  public int hashCode() {
    return medicalName != null ? medicalName.hashCode() : 0;
  }

  // -------------------------------------------------------------------------
  // INNER ENUM
  // -------------------------------------------------------------------------
  /**
   * High-level categorization of muscles into major anatomical groups.
   *
   * <p>This enumeration allows for a macroscopic analysis of training volume. It helps answer
   * questions like "Is the athlete training their Upper Body enough compared to their Lower Body?".
   */
  @Getter
  @RequiredArgsConstructor
  public enum MuscleGroup {
    LEGS("Legs"),
    BACK("Back"),
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    CORE("Core");

    /** A human-readable name suitable for UI display. */
    private final String displayName;
  }
}
