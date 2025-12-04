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
public class MovementSet {

  private Integer setNumber;
  private Integer targetReps;
  private Integer completedReps;
  private Double weight; // kg
  private Integer durationSeconds;
  private Double distance; // meters
  private Integer calories;

  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ScalingLevel scalingLevel = ScalingLevel.RX;

  @Builder.Default private Boolean completed = false;

  private String notes;
}
