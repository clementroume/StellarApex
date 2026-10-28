package apex.stellar.antares.dto;

import apex.stellar.antares.model.Division;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Athlete profile of the current user. Every field is optional: null clears it.
 *
 * @param division Competition division (MEN, WOMEN): load highlighted, separate leaderboards.
 * @param birthYear Birth year, for age divisions (private).
 * @param heightCm Height in centimetres (private).
 */
public record AthleteProfileRequest(
    @Schema(description = "Competition division, null when not chosen", nullable = true)
        Division division,
    @Schema(description = "Birth year (private)", example = "1990", nullable = true)
        @Min(value = 1900, message = "{validation.birthYear.range}")
        @Max(value = 2100, message = "{validation.birthYear.range}")
        Integer birthYear,
    @Schema(description = "Height in centimetres (private)", example = "178", nullable = true)
        @Min(value = 100, message = "{validation.height.range}")
        @Max(value = 250, message = "{validation.height.range}")
        Integer heightCm) {}
