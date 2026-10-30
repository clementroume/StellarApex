package apex.stellar.aldebaran.dto;

import java.util.Map;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Values available to filter the WOD list, computed with the same security and scope as the list.
 *
 * @param wodTypes WOD types present for the current filters (the type filter itself is ignored).
 * @param gymIds Gyms owning readable WODs (admin gym selector).
 * @param authorIds Authors of readable personal WODs (admin athlete selector).
 */
@Schema(description = "Values available to filter the WOD list")
public record WodFacetsResponse(
    @Schema(description = "WOD formats present, in enum order") List<WodType> wodTypes,
    @Schema(description = "WOD categories present, in enum order") List<WodCategory> categories,
    @Schema(description = "Gym IDs owning readable WODs") List<Long> gymIds,
    @Schema(description = "Author IDs of readable personal WODs") List<Long> authorIds,
    @Schema(description = "Number of WODs per category (NONE = without category), format ignored")
        Map<String, Long> categoryCounts,
    @Schema(description = "Number of WODs for the search and scope, every category") long total,
    @Schema(description = "Sub-categories present for the format asked, in enum order")
        List<WodStructure> structures) {

  /** Facets without counts nor sub-categories. */
  public WodFacetsResponse(
      List<WodType> wodTypes, List<WodCategory> categories, List<Long> gymIds, List<Long> authorIds) {
    this(wodTypes, categories, gymIds, authorIds, Map.of(), 0, List.of());
  }
}
