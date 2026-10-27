package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public display name of a user ("Clément R."): first name and last-name initial only.
 *
 * @param id The user ID.
 * @param displayName The display name.
 */
public record DisplayNameResponse(
    @Schema(description = "User ID", example = "12") Long id,
    @Schema(description = "First name and last-name initial", example = "Clément R.")
        String displayName) {}
