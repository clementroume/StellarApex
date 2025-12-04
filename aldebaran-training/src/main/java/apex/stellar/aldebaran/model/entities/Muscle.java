package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a specific anatomical muscle in the human body.
 *
 * <p>This entity serves as the granular unit for biomechanical analysis. Muscles are categorized by
 * {@link MuscleGroup} to facilitate high-level reporting and visualization (e.g., "Upper Body" vs.
 * "Lower Body" volume).
 */
@Entity
@Table(name = "muscles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Muscle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String standardName; // Latin/Medical name (e.g., "Pectoralis Major")

  private String nameEn; // Common name EN
  private String nameFr; // Common name FR

  @Column(columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(columnDefinition = "TEXT")
  private String descriptionFr;

  /** The major anatomical group this muscle belongs to. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MuscleGroup muscleGroup;

  /**
   * High-level categorization of muscles into major anatomical groups.
   *
   * <p>This enumeration allows for a macroscopic analysis of training volume. It helps answer
   * questions like "Is the athlete training their Upper Body enough compared to their Lower Body?".
   */
  @Getter
  @RequiredArgsConstructor
  public enum MuscleGroup {

    // --- Lower Body ---
    LEGS("Legs"),

    // --- Upper Body ---
    BACK("Back"),
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),

    // --- Trunk ---
    CORE("Core");

    /** A human-readable name suitable for UI display. */
    private final String displayName;
  }
}
