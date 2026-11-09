package apex.stellar.aldebaran.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * A usual name of a movement: techniques and equipment, nothing of its own. A technique of a
 * chain's component is written {@code 1:POWER}.
 *
 * @param nameEn English name.
 * @param nameFr French name, null when identical.
 * @param searchTerms Extra terms of the search box.
 * @param techniques Techniques the name stands for.
 * @param equipment Equipment the name stands for.
 * @param descriptionEn English description, or null.
 * @param descriptionFr French description, or null.
 * @param videoUrl Demonstration video, or null.
 */
public record UsualNameDto(
    @NotBlank @Size(max = 80) String nameEn,
    @Size(max = 80) String nameFr,
    @Size(max = 255) String searchTerms,
    Set<String> techniques,
    Set<String> equipment,
    @Size(max = 4000) String descriptionEn,
    @Size(max = 4000) String descriptionFr,
    @Size(max = 512) String videoUrl) {}
