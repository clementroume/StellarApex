package apex.stellar.aldebaran.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A name athletes use for a movement with given techniques and equipment (Power Clean = Clean +
 * POWER). It has no identity of its own: records and roles belong to the movement. Techniques of a
 * chained movement's component are written {@code 1:POWER}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movement_usual_names")
public class MovementUsualName {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "movement_id", nullable = false)
  private Movement movement;

  @Column(name = "name_en", nullable = false, length = 80)
  private String nameEn;

  @Column(name = "name_fr", length = 80)
  private String nameFr;

  @Column(name = "search_terms", length = 255)
  private String searchTerms;

  @Column(name = "techniques", nullable = false, length = 200)
  @Convert(converter = StringSetConverter.class)
  @Builder.Default
  private Set<String> techniques = new LinkedHashSet<>();

  @Column(name = "equipment", nullable = false, length = 100)
  @Convert(converter = StringSetConverter.class)
  @Builder.Default
  private Set<String> equipment = new LinkedHashSet<>();

  @Column(name = "description_en", columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(name = "description_fr", columnDefinition = "TEXT")
  private String descriptionFr;

  @Column(name = "video_url", length = 512)
  private String videoUrl;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof MovementUsualName other && id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
