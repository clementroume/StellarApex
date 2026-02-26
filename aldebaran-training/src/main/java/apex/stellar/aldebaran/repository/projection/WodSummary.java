package apex.stellar.aldebaran.repository.projection;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import java.time.LocalDateTime;

/**
 * Represents a summary projection of a workout definition (WOD). This interface is designed to
 * provide a lightweight view of a WOD without loading unnecessary details or relationships.
 * Typically used for listing or search scenarios.
 */
@SuppressWarnings("unused")
public interface WodSummary {

  /**
   * Retrieves the unique identifier of the workout definition (WOD).
   *
   * @return the unique identifier as a {@code Long}.
   */
  Long getId();

  /**
   * Retrieves the title or name of the workout.
   *
   * @return the title as a {@code String}.
   */
  String getTitle();

  /**
   * Retrieves the classification type of the workout (e.g., AMRAP, EMOM, For Time).
   *
   * @return the {@link WodType}.
   */
  WodType getWodType();

  /**
   * Retrieves the primary metric used to score the workout (e.g., Time, Rounds, Reps).
   *
   * @return the {@link ScoreType}.
   */
  ScoreType getScoreType();

  /**
   * Retrieves the repetition scheme or structure description of the workout.
   *
   * @return the rep scheme as a {@code String}.
   */
  String getRepScheme();

  /**
   * Retrieves the time limit allocated for the workout in seconds.
   *
   * @return the time cap in seconds, or {@code null} if no time cap is set.
   */
  Integer getTimeCapSeconds();

  /**
   * Retrieves the timestamp when the workout definition was created.
   *
   * @return the creation date and time as a {@link LocalDateTime}.
   */
  LocalDateTime getCreatedAt();
}
