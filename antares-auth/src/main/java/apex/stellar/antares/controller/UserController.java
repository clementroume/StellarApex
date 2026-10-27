package apex.stellar.antares.controller;

import apex.stellar.antares.config.SupportSessionAuthentication;
import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.DisplayNameResponse;
import apex.stellar.antares.dto.DisplayNamesRequest;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.model.User;
import apex.stellar.antares.service.CookieService;
import apex.stellar.antares.service.JwtService;
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
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing the currently authenticated user's account.
 *
 * <p>All endpoints in this controller are protected and require a valid session. It handles profile
 * retrieval, updates, password changes, and account deletion.
 */
@RestController
@RequestMapping("/antares/users")
@RequiredArgsConstructor
@Tag(
    name = "User Management",
    description = "Profile, preferences, and password management for the current user.")
public class UserController {

  private final UserService userService;
  private final SupportSessionService supportSessionService;
  private final CookieService cookieService;
  private final JwtService jwtService;

  /**
   * Retrieves the profile of the currently authenticated user.
   *
   * @param authentication The authentication object containing the user's principal.
   * @return A ResponseEntity containing the {@link UserResponse} for the current user.
   */
  @GetMapping("/me")
  @Operation(
      summary = "Get current user profile",
      description =
          "Retrieves public details of the currently authenticated user. During a support session"
              + " this is the target user, with `supportSession` describing the acting admin.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - Token missing or invalid",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> getAuthenticatedUser(Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(
          withSupportSession(userService.getProfile(currentUser), authentication));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Updates the authenticated user's core profile information (Name, Email).
   *
   * @param request The DTO with the updated user data.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PutMapping("/me/profile")
  @Operation(
      summary = "Update profile info",
      description = "Updates first name, last name, and email. Email uniqueness is checked.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error (e.g. invalid email format)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - Email already in use",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> updateProfile(
      @Valid @RequestBody ProfileUpdateRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(
          withSupportSession(userService.updateProfile(currentUser, request), authentication));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Partially updates the authenticated user's preferences (Locale, Theme).
   *
   * @param request The DTO with the updated preferences' data.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PatchMapping("/me/preferences")
  @Operation(summary = "Update user preferences", description = "Updates locale, UI theme and load unit.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> updatePreferences(
      @Valid @RequestBody PreferencesUpdateRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(
          withSupportSession(userService.updatePreferences(currentUser, request), authentication));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Resolves the display names the authenticated user may see (e.g. the athletes of a
   * leaderboard). Hidden users are left out of the response.
   *
   * @param request The user IDs to resolve (at most 100).
   * @param authentication The current user's authentication principal.
   * @return The visible display names.
   */
  @PostMapping("/display-names")
  @Operation(
      summary = "Resolve display names",
      description =
          "Returns \"First L.\" for the users the caller may see: themselves, members of their gyms,"
              + " users who opted in to public leaderboards (all for platform admins).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Visible display names"),
        @ApiResponse(
            responseCode = "400",
            description = "More than 100 IDs",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull List<DisplayNameResponse>> getDisplayNames(
      @Valid @RequestBody DisplayNamesRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(userService.getDisplayNames(currentUser, request.userIds()));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Changes the authenticated user's password.
   *
   * @param request The DTO with current and new passwords.
   * @param authentication The current user's authentication principal.
   * @return 200 OK on success.
   */
  @PutMapping("/me/password")
  @Operation(
      summary = "Change password",
      description =
          "Updates the user's password. Requires current password verification. Refused during a"
              + " support session (403, code SUPPORT_SESSION_FORBIDDEN).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input (e.g. wrong current password, mismatch)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      userService.changePassword(request, currentUser);
      return ResponseEntity.ok().build();
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Permanently deletes the authenticated user's account and clears auth cookies.
   *
   * @param authentication The authentication object.
   * @param response The HTTP response to clear cookies.
   * @return 204 No Content on success.
   */
  @DeleteMapping("/me")
  @Operation(
      summary = "Delete account",
      description =
          "Permanently deletes the authenticated user account and clears sessions. Refused during"
              + " a support session (403, code SUPPORT_SESSION_FORBIDDEN).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Account successfully deleted"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull Void> deleteAccount(
      Authentication authentication, HttpServletResponse response) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      userService.deleteAccount(currentUser);
      cookieService.clearCookie(jwtService.getAccessTokenCookieName(), response);
      cookieService.clearCookie(jwtService.getRefreshTokenCookieName(), response);
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Adds the active support session to a profile response. Every endpoint returning the current
   * user must use it: Sirius replaces its user with the response, and a missing session would make
   * the UI believe the support session ended.
   *
   * @param profile the profile of the request's user (the target during a support session).
   * @param authentication the request authentication.
   * @return the profile, with {@code supportSession} set during a support session.
   */
  private UserResponse withSupportSession(UserResponse profile, Authentication authentication) {
    return authentication instanceof SupportSessionAuthentication support
        ? profile.withSupportSession(supportSessionService.toInfo(support))
        : profile;
  }
}
