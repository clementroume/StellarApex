package apex.stellar.antares.demo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a visitor needs to try the demo accounts: their shared password. Public on purpose (roadmap
 * D68): the accounts hold no privilege and never become administrators.
 *
 * @param password The password shared by every demo account.
 */
public record DemoAccessResponse(
    @Schema(description = "Password shared by the demo accounts") String password) {}
