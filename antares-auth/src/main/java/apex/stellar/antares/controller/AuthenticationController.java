package apex.stellar.antares.controller;

import static java.nio.charset.StandardCharsets.UTF_8;

import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.dto.TokenRefreshResponse;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import apex.stellar.antares.service.AuthenticationService;
import apex.stellar.antares.service.JwtService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling user authentication endpoints such as registration, login, logout,
 * and token refresh.
 */
@RestController
@RequestMapping("/antares/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, Logging in, and Token Management")
@Slf4j
public class AuthenticationController {

  private final AuthenticationService authenticationService;
  private final JwtService jwtService;
  private final UserRepository userRepository;

  @Value("${application.frontend.login.url}")
  private String loginBaseUrl;

  /**
   * Handles POST requests to register a new user.
   *
   * @param request The registration request DTO, validated.
   * @param response The HTTP response, used to set auth cookies.
   * @return A ResponseEntity with status 201 (Created) and the new {@link UserResponse}.
   */
  @PostMapping("/register")
  @Operation(
      summary = "Register a new user",
      description = "Creates a new user account and returns the public profile.")
  @SecurityRequirements()
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "User successfully created"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error (e.g. invalid email format)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "409",
            description = "Conflict - Email address is already in use",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(authenticationService.register(request, response));
  }

  /**
   * Handles POST requests to authenticate (login) a user.
   *
   * @param request The authentication request DTO, validated.
   * @param response The HTTP response, used to set auth cookies.
   * @return A ResponseEntity with status 200 (OK) and the authenticated {@link UserResponse}.
   */
  @PostMapping("/login")
  @Operation(
      summary = "Authenticate user",
      description = "Validates credentials and sets HttpOnly cookies.")
  @SecurityRequirements()
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Authentication successful"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error (e.g. missing field)",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Bad Credentials",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "429",
            description = "Account locked due to too many failed attempts",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull UserResponse> login(
      @Valid @RequestBody AuthenticationRequest request, HttpServletResponse response) {

    return ResponseEntity.ok(authenticationService.login(request, response));
  }

  /**
   * Handles POST requests to log out the currently authenticated user.
   *
   * @param authentication The Spring Security authentication object (injected).
   * @param response The HTTP response, used to clear auth cookies.
   * @return A ResponseEntity with status 200 (OK).
   */
  @PostMapping("/logout")
  @Operation(summary = "Logout user", description = "Invalidates the session and clears cookies.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Logged out successfully"),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized - User was not logged in",
            content = @Content(schema = @Schema(hidden = true)))
      })
  public ResponseEntity<@NonNull Void> logout(
      Authentication authentication, HttpServletResponse response) {

    User currentUser = (User) authentication.getPrincipal();
    authenticationService.logout(currentUser, response);

    return ResponseEntity.ok().build();
  }

  /**
   * Advanced Forward Auth Endpoint.
   *
   * <p>This method acts as a gatekeeper for infrastructure services (Traefik Dashboard, Vega
   * Admin).
   *
   * <p>Logic Flow:
   *
   * <ol>
   *   <li>If Unauthenticated -> Return 302 Redirect to the Sirius Login Page.
   *   <li>If Authenticated but not ADMIN -> Return 403 Forbidden.
   *   <li>If Authenticated and ADMIN -> Return 200 OK (Access Granted).
   * </ol>
   */
  @GetMapping("/verify/admin")
  @Hidden
  public ResponseEntity<Void> verifyAdmin(
      HttpServletRequest request, Authentication authentication) {

    if (authentication == null || !authentication.isAuthenticated()) {

      return ResponseEntity.status(HttpStatus.FOUND)
          .header(HttpHeaders.LOCATION, buildLoginRedirectUrl(request))
          .build();
    }

    if (authentication.getPrincipal() instanceof User user) {

      return user.getPlatformRole() == PlatformRole.ADMIN
          ? ResponseEntity.ok().build()
          : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
  }

  /**
   * Verifies the authenticated user's API access and responds with appropriate headers containing
   * user details if the authentication is valid and the user is authenticated.
   *
   * @param authentication the authentication object containing the user's authentication details
   * @param request the HTTP request containing headers
   * @return a ResponseEntity with an HTTP 200 status and user headers if authenticated, or an HTTP
   *     401 status if the user is not authenticated or the authentication is null
   */
  // In AuthenticationController.java

  @GetMapping("/verify/api")
  @Hidden
  @Transactional(readOnly = true)
  public ResponseEntity<Void> verifyApi(Authentication authentication, HttpServletRequest request) {

    if (authentication == null || !authentication.isAuthenticated()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (authentication.getPrincipal() instanceof User principal) {
      User user = userRepository.findById(principal.getId()).orElse(principal);
      String gymIdHeader = request.getHeader("X-Context-Gym-Id");

      // Case 1: Independent Athlete (No Gym Context)
      if (gymIdHeader == null) {
        return ResponseEntity.ok()
            .header("X-Auth-User-Id", String.valueOf(user.getId()))
            .header("X-Auth-User-Role", user.getPlatformRole().name())
            .header("X-Auth-User-Locale", user.getLocale())
            .build();
      }

      // Case 2: Gym Context
      try {
        Long gymId = Long.parseLong(gymIdHeader);
        Membership membership =
            user.getMemberships().stream()
                .filter(m -> m.getGym().getId().equals(gymId))
                .findFirst()
                .orElse(null);

        // Check Membership Existence & Status
        if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Check Gym Status (Allow Owner/Programmer to access pending gyms)
        Gym gym = membership.getGym();
        boolean isStaff =
            membership.getGymRole() == GymRole.OWNER
                || membership.getGymRole() == GymRole.PROGRAMMER;
        if (gym.getStatus() != GymStatus.ACTIVE && !isStaff) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String permissions =
            membership.getPermissions().stream()
                .map(Permission::name)
                .collect(Collectors.joining(","));

        return ResponseEntity.ok()
            .header("X-Auth-User-Id", String.valueOf(user.getId()))
            .header("X-Auth-User-Locale", user.getLocale())
            .header("X-Auth-Gym-Id", String.valueOf(gymId))
            .header("X-Auth-User-Role", membership.getGymRole().name())
            .header("X-Auth-User-Permissions", permissions)
            .build();

      } catch (NumberFormatException e) {
        log.warn("Invalid Gym ID format: {}", gymIdHeader, e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
      }
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
  }

  /**
   * Handles POST requests to refresh an expired access token using a refresh token. The refresh
   * token is read from an HttpOnly cookie.
   *
   * @param request The HTTP request, used to read cookies.
   * @param response The HTTP response, used to set new auth cookies.
   * @return A ResponseEntity with status 200 (OK) and the {@link TokenRefreshResponse}.
   * @throws ResourceNotFoundException if the refresh token cookie is missing.
   */
  @PostMapping("/refresh-token")
  @Hidden
  public ResponseEntity<@NonNull TokenRefreshResponse> refreshToken(
      HttpServletRequest request, HttpServletResponse response) {

    String oldRefreshToken =
        jwtService.getJwtFromCookies(request, jwtService.getRefreshTokenCookieName());

    if (oldRefreshToken == null) {
      throw new ResourceNotFoundException("error.token.refresh.missing");
    }

    return ResponseEntity.ok(authenticationService.refreshToken(oldRefreshToken, response));
  }

  /**
   * Helper to construct the Angular login URL with a returnUrl parameter. It uses the X-Forwarded-*
   * headers provided by Traefik to know where the user came from.
   */
  private String buildLoginRedirectUrl(HttpServletRequest request) {

    String proto = request.getHeader("X-Forwarded-Proto");
    String host = request.getHeader("X-Forwarded-Host");
    String uri = request.getHeader("X-Forwarded-Uri");

    if (proto != null && host != null) {
      String originalUrl = proto + "://" + host + (uri != null ? uri : "");
      String encodedUrl = URLEncoder.encode(originalUrl, UTF_8);
      return loginBaseUrl + "?returnUrl=" + encodedUrl;
    }

    return loginBaseUrl;
  }
}
