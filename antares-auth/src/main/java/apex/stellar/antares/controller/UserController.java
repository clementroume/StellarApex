package apex.stellar.antares.controller;

import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.PreferencesUpdateRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.User;
import apex.stellar.antares.service.CookieService;
import apex.stellar.antares.service.JwtService;
import apex.stellar.antares.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing the currently authenticated user's account. All endpoints in this
 * controller are protected and require a valid session.
 */
@RestController
@RequestMapping("/antares/users")
@RequiredArgsConstructor
@Tag(
    name = "User Management",
    description = "Profile, preferences, and password management for the current user.")
public class UserController {

  private final UserService userService;
  private final UserMapper userMapper;
  private final CookieService cookieService;
  private final JwtService jwtService;

  /**
   * Handles GET requests to retrieve the profile of the currently authenticated user. The user is
   * identified via the Authentication principal injected by Spring Security.
   *
   * @param authentication The authentication object containing the user's principal.
   * @return A ResponseEntity containing the {@link UserResponse} for the current user.
   */
  @GetMapping("/me")
  @Operation(
      summary = "Get current user profile",
      description = "Retrieves public details of the currently authenticated user.")
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
      return ResponseEntity.ok(userMapper.toUserResponse(currentUser));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles PUT requests to update the authenticated user's core profile information.
   *
   * @param request The DTO with the updated user data, which is validated.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PutMapping("/me/profile")
  @Operation(
      summary = "Update profile info",
      description = "Updates first name, last name, and email.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error (e.g. invalid email format)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> updateProfile(
      @Valid @RequestBody ProfileUpdateRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(userService.updateProfile(currentUser, request));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles PATCH requests to partially update the authenticated user's preferences.
   *
   * @param request The DTO with the updated preferences data, which is validated.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PatchMapping("/me/preferences")
  @Operation(summary = "Update user preferences", description = "Updates locale and UI theme.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error (e.g. invalid locale or theme)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> updatePreferences(
      @Valid @RequestBody PreferencesUpdateRequest request, Authentication authentication) {

    if (authentication.getPrincipal() instanceof User currentUser) {
      return ResponseEntity.ok(userService.updatePreferences(currentUser, request));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles PUT requests to change the authenticated user's password.
   *
   * @param request The DTO with the current, new, and confirmation passwords, validated.
   * @param authentication The current user's authentication principal.
   * @return An empty ResponseEntity (200 OK) confirming success.
   */
  @PutMapping("/me/password")
  @Operation(
      summary = "Change password",
      description = "Updates the user's password. Requires current password verification.")
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
   * Deletes the authenticated user's account and clears all associated sessions. This action is
   * irreversible and removes all data related to the user's account.
   *
   * @param authentication the authentication object representing the currently logged-in user
   * @param response the HTTP servlet response used to manipulate response headers or cookies
   * @return a ResponseEntity with status 204 (No Content) if the account is successfully deleted,
   *     or status 401 (Unauthorized) if the authentication is invalid
   */
  @DeleteMapping("/me")
  @Operation(
      summary = "Delete account",
      description = "Permanently deletes the authenticated user account and clears sessions.")
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
}
