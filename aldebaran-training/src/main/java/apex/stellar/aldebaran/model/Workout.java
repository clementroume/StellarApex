package apex.stellar.aldebaran.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
@Table(
    name = "workouts",
    indexes = {
      @Index(name = "idx_user_date", columnList = "userId, workoutDate"),
      @Index(name = "idx_benchmark", columnList = "benchmarkName"),
      @Index(name = "idx_type", columnList = "type")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDate workoutDate;

  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkoutType type;

  // For benchmark workouts
  @Enumerated(EnumType.STRING)
  private BenchmarkName benchmarkName;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "workout_modalities", joinColumns = @JoinColumn(name = "workout_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "modality")
  @Builder.Default
  private Set<Modality> modalities = new HashSet<>();

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(columnDefinition = "TEXT")
  private String notes;

  // For AMRAP/EMOM
  private Integer timeCap; // seconds

  // For EMOM
  private Integer emomInterval; // seconds (usually 60)
  private Integer emomRounds; // total rounds

  // For specific workout structures
  private String repScheme; // "21-15-9", "5-5-5-3-3-3-1-1-1", etc.

  @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<WorkoutMovement> movements = new ArrayList<>();

  @Embedded private WorkoutScore score;

  private Integer actualDurationSeconds;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
