package apex.stellar.aldebaran.repository.projection;

import apex.stellar.aldebaran.model.enums.Category;

/** Projection interface providing a summary view of a Movement entity. */
@SuppressWarnings("unused")
public interface MovementSummary {
  /**
   * Gets the unique identifier of the movement.
   *
   * @return the movement ID
   */
  String getId();

  /**
   * Gets the full name of the movement.
   *
   * @return the movement name
   */
  String getName();

  /**
   * Gets the short-form abbreviation of the movement name.
   *
   * @return the name abbreviation
   */
  String getNameAbbreviation();

  /**
   * Gets the classification category of the movement.
   *
   * @return the movement category
   */
  Category getCategory();

  /**
   * Gets the URL for the movement's illustrative image.
   *
   * @return the image URL
   */
  String getImageUrl();
}
