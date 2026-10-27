package apex.stellar.antares.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Users whose display names a client needs, e.g. the athletes of a leaderboard page.
 *
 * @param userIds The user IDs (at most 100).
 */
public record DisplayNamesRequest(
    @Schema(description = "User IDs to resolve", example = "[12, 15]")
        @NotNull(message = "{validation.userIds.required}")
        @Size(max = 100, message = "{validation.userIds.size}")
        List<@NotNull Long> userIds) {}
