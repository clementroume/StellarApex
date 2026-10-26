package apex.stellar.antares.controller;

import apex.stellar.antares.dto.SupportSessionRequest;
import apex.stellar.antares.dto.SupportSessionResponse;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.dto.UserSummary;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.service.SupportSessionService;
import apex.stellar.antares.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform administration endpoints: user search and audited support sessions (acting as a real
 * user, e.g. to reproduce a bug reported by a gym).
 */
@RestController
@RequestMapping("/antares/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Platform admin tools: user search, support sessions")
public class AdminController {

  private final UserService userService;
  private final SupportSessionService supportSessionService;

  /**
   * Searches users by first name, last name or email, optionally narrowed to a gym membership.
   *
   * @param search the text to match (case-insensitive); blank lists every user.
   * @param gymId optional gym the user must belong to.
   * @param role optional role of that membership.
   * @param status optional status of that membership.
   * @param pageable page and size (default 20, capped at 50).
   * @return a slice of users with their memberships, ordered by last name.
   */
  @GetMapping("/users")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Search users",
      description =
          "Platform Admin only. Paginated users matching first name, last name or email, optionally"
              + " filtered by gym, membership role and membership status.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Matching users"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin only",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Slice<UserResponse>> searchUsers(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) Long gymId,
      @RequestParam(required = false) GymRole role,
      @RequestParam(required = false) MembershipStatus status,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(userService.searchUsers(search, gymId, role, status, pageable));
  }

  /**
   * Resolves user ids into summaries (e.g. names of WOD authors in the admin WOD filters).
   *
   * @param ids the user ids.
   * @return the summaries of the existing users.
   */
  @GetMapping("/users/lookup")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Resolve users", description = "Platform Admin only. Summaries by ids.")
  public ResponseEntity<List<UserSummary>> lookupUsers(@RequestParam List<Long> ids) {
    return ResponseEntity.ok(userService.lookup(ids));
  }

  /**
   * Lists the users the admin acted as most recently through support sessions.
   *
   * @param admin the authenticated platform admin.
   * @return up to 5 users with their memberships, latest first.
   */
  @GetMapping("/support-sessions/recent")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Recent support targets",
      description = "Platform Admin only. Last 5 users acted as, latest first.")
  public ResponseEntity<List<UserResponse>> recentSupportTargets(
      @AuthenticationPrincipal User admin) {
    return ResponseEntity.ok(
        userService.loadWithMemberships(supportSessionService.recentTargetIds(admin)));
  }

  /**
   * Starts a 30-minute support session as another user and sets the {@code support_token} cookie.
   *
   * @param request the target user and the audited reason.
   * @param admin the authenticated platform admin.
   * @param response the HTTP response receiving the cookie.
   * @return 201 with the started session.
   */
  @PostMapping("/support-sessions")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Start a support session",
      description =
          "Platform Admin only. Acts as the given (non-admin) user for 30 minutes, never"
              + " refreshed; the admin's own session is kept. Audited.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Support session started"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error (missing user or reason, reason too long)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "403",
            description =
                "Forbidden - Admin only, target is an admin, or already in a support session"
                    + " (code SUPPORT_SESSION_FORBIDDEN)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "A support session is already active for this admin",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<SupportSessionResponse> startSupportSession(
      @Valid @RequestBody SupportSessionRequest request,
      @AuthenticationPrincipal User admin,
      HttpServletResponse response) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(supportSessionService.start(admin, request, response));
  }

  /**
   * Ends the admin's current support session and clears its cookie. Idempotent.
   *
   * <p>The support cookie is ignored on this endpoint, so it is authorized on the admin's own
   * access token.
   *
   * @param admin the authenticated platform admin.
   * @param response the HTTP response receiving the cleared cookie.
   * @return 204, even without an active session.
   */
  @DeleteMapping("/support-sessions/current")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "End the current support session",
      description = "Platform Admin only. Idempotent: 204 even without an active session.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Support session ended"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin only",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<Void> endSupportSession(
      @AuthenticationPrincipal User admin, HttpServletResponse response) {
    supportSessionService.endActiveSessions(admin, response);
    return ResponseEntity.noContent().build();
  }
}
