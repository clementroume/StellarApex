package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The platform admin acting through a support session.
 *
 * @param id The admin's user ID.
 * @param firstName The admin's first name.
 * @param lastName The admin's last name.
 */
@Schema(description = "Admin acting through a support session")
public record SupportActor(
    @Schema(description = "Admin user ID", example = "1") Long id,
    @Schema(description = "First Name", example = "Jane") String firstName,
    @Schema(description = "Last Name", example = "Admin") String lastName) {}
