package apex.stellar.aldebaran.model.entities;

import apex.stellar.aldebaran.model.emuns.MuscleGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
  private String standardName;

  private String nameEn;
  private String nameFr;

  @Column(columnDefinition = "TEXT")
  private String descriptionEn;

  @Column(columnDefinition = "TEXT")
  private String descriptionFr;

  @Enumerated(EnumType.STRING)
  private MuscleGroup muscleGroup;
}
