package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A body weight measure. A second measure on the same day replaces the first.
 *
 * @param weightKg Weight in kilograms (Sirius converts from pounds).
 * @param measuredOn Day of the measure (the user's local day); today when null.
 */
public record BodyWeightRequest(
    @Schema(description = "Weight in kilograms", example = "78.5")
        @NotNull(message = "{validation.bodyWeight.required}")
        @DecimalMin(value = "20", message = "{validation.bodyWeight.range}")
        @DecimalMax(value = "400", message = "{validation.bodyWeight.range}")
        BigDecimal weightKg,
    @Schema(
            description =
                "Day of the measure in the user's time zone, today (UTC) when null; at most one day"
                    + " after today in UTC",
            example = "2026-10-28")
        LocalDate measuredOn) {}
