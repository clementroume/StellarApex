package apex.stellar.aldebaran.dto;

import java.util.Set;

/**
 * Where an inherited record really comes from.
 *
 * @param movementId The movement actually lifted.
 * @param movementName Its name.
 * @param variantKey Its variant key.
 * @param equipment Equipment of that variant.
 * @param techniques Techniques of that variant.
 */
public record RecordSourceResponse(
    Long movementId,
    String movementName,
    String variantKey,
    Set<String> equipment,
    Set<String> techniques) {}
