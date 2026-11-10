package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.enums.JointAction;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents an anatomical muscle, or a functional group of muscles (e.g. Quadriceps Femoris).
 *
 * <p>A muscle may belong to one functional group ({@link #parent}); groups do not nest. A movement
 * can target a group or one of its muscles when that muscle has a specific action.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "muscles")
@EntityListeners(AuditingEntityListener.class)
public class Muscle {

  // --- Identification ---
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "medical_name", unique = true, nullable = false, length = 100)
  @NotBlank
  private String medicalName;

  /**
   * Stable key linking this muscle to its 3D model and images (see {@code
   * sirius-app/tools/muscle-model}). Derived once from the medical name, never changed afterwards,
   * so renaming a muscle keeps the link.
   */
  @Column(name = "code", unique = true, nullable = false, updatable = false, length = 100)
  private String code;

  // --- Characteristics ---
  @Enumerated(EnumType.STRING)
  @Column(name = "region", nullable = false, length = 50)
  @NotNull
  private MuscleRegion region;

  /** Functional group of this muscle; null for a group or a standalone muscle. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private Muscle parent;

  /** Joint actions this muscle can produce (control of the catalog roles). */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "muscle_joint_actions", joinColumns = @JoinColumn(name = "muscle_id"))
  @Column(name = "action", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private Set<JointAction> jointActions = new HashSet<>();

  /** Muscles of this functional group; empty unless this muscle is a group. */
  @OneToMany(mappedBy = "parent")
  @OrderBy("medicalName")
  @Builder.Default
  private List<Muscle> members = new ArrayList<>();

  // --- Internationalized Content ---
  @Column(name = "common_name_en", length = 100)
  private String commonNameEn;

  @Column(name = "common_name_fr", length = 100)
  private String commonNameFr;

  @Column(name = "description_en", columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(name = "description_fr", columnDefinition = "TEXT")
  private String descriptionFr;

  // --- Media ---
  @Column(name = "image_url")
  private String imageUrl;

  // --- Audit ---
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * The code a medical name gives: lower case, every run of other characters replaced by one dash
   * (same rule as the model pipeline).
   *
   * @param medicalName The medical name.
   * @return The code, {@code "muscle"} when the name holds no latin letter or digit.
   */
  public static String codeOf(String medicalName) {
    String code =
        medicalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return code.isEmpty() ? "muscle" : code;
  }

  @PrePersist
  void assignCode() {
    if (code == null) {
      code = codeOf(medicalName);
    }
  }

  // --- Equality & Hashing ---
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Muscle other)) {
      return false;
    }
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  /** Region of the body a muscle belongs to, by its proximal attachment. */
  public enum MuscleRegion {
    THORAX,
    BACK,
    ABDOMEN_PELVIS,
    SHOULDER,
    ARM_FOREARM,
    HIP,
    THIGH_LEG
  }
}
