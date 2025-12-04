package apex.stellar.aldebaran.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutScore {

  @Enumerated(EnumType.STRING)
  private ScoreType scoreType;

  @Enumerated(EnumType.STRING)
  private ScalingLevel scalingLevel;

  // For TIME scoring
  private Integer timeSeconds;

  // For ROUNDS_REPS scoring (AMRAP)
  private Integer rounds;
  private Integer reps;

  // For WEIGHT scoring
  private Double maxWeight; // kg

  // For REPS scoring
  private Integer totalReps;

  // For CALORIES/DISTANCE scoring
  private Integer totalCalories;
  private Double totalDistance; // meters

  // For LOAD scoring (total weight moved)
  private Double totalLoad; // kg

  private String notes;

  // Time cap indicator
  private Boolean timeCapped;
  private Integer timeCapSeconds;
}
