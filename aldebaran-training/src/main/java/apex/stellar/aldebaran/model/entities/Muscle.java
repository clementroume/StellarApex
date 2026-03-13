package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Represents a specific anatomical muscle in the human body. */
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

  // --- Characteristics ---
  @Enumerated(EnumType.STRING)
  @Column(name = "muscle_group", nullable = false, length = 50)
  @NotNull
  private MuscleGroup muscleGroup;

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

  /** MuscleGroup Inner Enum. */
  @Getter
  @RequiredArgsConstructor
  public enum MuscleGroup {
    LEGS,
    BACK,
    CHEST,
    SHOULDERS,
    ARMS,
    CORE
  }
}
