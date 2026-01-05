package apex.stellar.aldebaran.repository.projection;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import java.time.LocalDateTime;

/**
 * Lightweight projection for listing WODs without loading movements. Used in listing and search
 * endpoints.
 */
public interface WodSummary {
  Long getId();

  String getTitle();

  WodType getWodType();

  ScoreType getScoreType();

  String getRepScheme();

  Integer getTimeCapSeconds();

  LocalDateTime getCreatedAt();
}
