package apex.stellar.aldebaran.dto;

import java.util.List;
import java.util.Set;

/**
 * An athlete's records for one variant set of a movement (D61).
 *
 * @param variantKey The variant key (empty for the base version).
 * @param usualNameEn Usual name of the variant in the catalog ("Zercher Squat"), or null.
 * @param base Whether this is the base version (shown first, used by % loads and the boards).
 * @param equipment The equipment of the variant (implied equipment excluded).
 * @param techniques The techniques of the variant.
 * @param estimatedOneRepMaxKg The Epley estimate from the records of 2 to 10 reps (D57), in kg, or
 *     null; displayed only, never a record.
 * @param records The records, by number of reps.
 * @param estimates Displayed estimates of the usual N RM without a record (1, 2, 3, 5, 10), from
 *     the 1RM (real, else estimated); never records.
 */
public record VariantRecordsResponse(
    String variantKey,
    String usualNameEn,
    boolean base,
    Set<String> equipment,
    Set<String> techniques,
    Double estimatedOneRepMaxKg,
    List<RecordEntryResponse> records,
    List<RecordEstimateResponse> estimates) {}
