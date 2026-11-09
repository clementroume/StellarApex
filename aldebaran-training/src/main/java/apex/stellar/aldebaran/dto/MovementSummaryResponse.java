package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import java.util.List;

/**
 * Lightweight DTO for listing movements without heavy details (anatomy, descriptions).
 *
 * <p>Used for autocomplete dropdowns and search results to optimize bandwidth.
 */
public record MovementSummaryResponse(
    Long id,
    String name,
    String nameAbbreviation,
    Category category,
    String imageUrl,
    List<String> componentNames,
    List<String> acceptedNames) {

  /**
   * The same summary with what the movement is made of.
   *
   * @param components Names of the steps of a chain, in order.
   * @param accepted Names of the movements an "either" movement accepts.
   * @return The completed summary.
   */
  public MovementSummaryResponse withComposition(List<String> components, List<String> accepted) {
    return new MovementSummaryResponse(
        id, name, nameAbbreviation, category, imageUrl, components, accepted);
  }
}
