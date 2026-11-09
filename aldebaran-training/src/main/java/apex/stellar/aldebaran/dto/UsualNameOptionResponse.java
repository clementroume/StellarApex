package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import java.util.Set;

/**
 * One entry of the movement search of the WOD forms.
 *
 * @param movementId The movement a choice selects.
 * @param movementName Its catalog name.
 * @param category Its category.
 * @param nameEn Usual name in English.
 * @param nameFr Usual name in French, null when identical.
 * @param searchTerms Extra search terms.
 * @param techniques Techniques preselected by this name.
 * @param equipment Equipment preselected by this name.
 * @param impliedEquipment Equipment the movement implies, or null.
 */
public record UsualNameOptionResponse(
    Long movementId,
    String movementName,
    Category category,
    String nameEn,
    String nameFr,
    String searchTerms,
    Set<String> techniques,
    Set<String> equipment,
    Equipment impliedEquipment) {}
