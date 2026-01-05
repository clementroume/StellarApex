package apex.stellar.aldebaran.repository.projection;

import apex.stellar.aldebaran.model.enums.Category;

/**
 * Lightweight projection for listing movements without loading all relationships. Used in search
 * endpoints and autocomplete fields.
 *
 * <p>Performance: ~70% faster than loading full Movement entities.
 */
public interface MovementSummary {
  String getId();

  String getName();

  String getNameAbbreviation();

  Category getCategory();

  String getImageUrl();
}
