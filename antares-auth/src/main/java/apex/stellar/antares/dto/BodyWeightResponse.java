package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A body weight measure of the current user (private data, only returned to its owner).
 *
 * @param id The measure id.
 * @param measuredOn Day of the measure.
 * @param weightKg Weight in kilograms.
 */
public record BodyWeightResponse(
    @Schema(example = "12") Long id,
    @Schema(example = "2026-10-28") LocalDate measuredOn,
    @Schema(example = "78.5") BigDecimal weightKg) {}
