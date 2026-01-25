package apex.stellar.antares.controller;

import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for administrative impersonation tasks.
 *
 * <p>This controller allows privileged users (Global Admins) to assume the identity of another user
 * for support or debugging purposes. It generates valid authentication tokens for the target user.
 */
@RestController
@RequestMapping("/antares/auth/impersonate")
@RequiredArgsConstructor
@Hidden
public class ImpersonationController {

  private final AuthenticationService authenticationService;

  /**
   * Generates authentication tokens for a specific user, effectively logging in as them.
   *
   * <p><b>Security:</b> Strictly restricted to users with the {@code ROLE_ADMIN} authority.
   *
   * @param userId The ID of the user to impersonate.
   * @param response The HTTP response (used to set the Refresh Token cookie).
   * @return A {@link UserResponse} containing the Access Token and user details.
   */
  @PostMapping("/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<UserResponse> impersonate(
      @PathVariable Long userId, HttpServletResponse response) {
    return ResponseEntity.ok(authenticationService.impersonate(userId, response));
  }
}
