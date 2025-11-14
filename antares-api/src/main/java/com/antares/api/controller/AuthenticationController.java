package com.antares.api.controller;

import com.antares.api.dto.AuthenticationRequest;
import com.antares.api.dto.RegisterRequest;
import com.antares.api.dto.TokenRefreshResponse;
import com.antares.api.dto.UserResponse;
import com.antares.api.exception.ResourceNotFoundException;
import com.antares.api.model.User;
import com.antares.api.service.AuthenticationService;
import com.antares.api.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling user authentication endpoints such as registration, login, logout,
 * and token refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

  private final AuthenticationService authenticationService;
  private final JwtService jwtService;

  /**
   * Handles POST requests to register a new user.
   *
   * @param request The registration request DTO, validated.
   * @param response The HTTP response, used to set auth cookies.
   * @return A ResponseEntity with status 201 (Created) and the new {@link UserResponse}.
   */
  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(
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
  public ResponseEntity<UserResponse> login(
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
  public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {

    User currentUser = (User) authentication.getPrincipal();
    authenticationService.logout(currentUser, response);

    return ResponseEntity.ok().build();
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
  public ResponseEntity<TokenRefreshResponse> refreshToken(
      HttpServletRequest request, HttpServletResponse response) {

    String oldRefreshToken =
        jwtService.getJwtFromCookies(request, jwtService.getRefreshTokenCookieName());

    if (oldRefreshToken == null) {
      throw new ResourceNotFoundException("error.token.refresh.missing");
    }

    TokenRefreshResponse refreshResponse =
        authenticationService.refreshToken(oldRefreshToken, response);

    return ResponseEntity.ok(refreshResponse);
  }
}
