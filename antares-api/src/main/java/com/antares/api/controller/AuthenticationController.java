package com.antares.api.controller;

import com.antares.api.annotation.RateLimit;
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
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthenticationController handles user authentication-related endpoints such as registration,
 * login, logout, and token refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

  private final AuthenticationService authenticationService;
  private final JwtService jwtService;

  /**
   * Registers a new user.
   *
   * @param request the registration request containing user details
   * @param response the HTTP response to set cookies if needed
   * @return ResponseEntity containing the registered user's details
   */
  @PostMapping("/register")
  @RateLimit(limit = 5, duration = 10, unit = TimeUnit.MINUTES)
  public ResponseEntity<UserResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(authenticationService.register(request, response));
  }

  /**
   * Authenticates a user and logs them in.
   *
   * @param request the authentication request containing user credentials
   * @param response the HTTP response to set cookies if needed
   * @return ResponseEntity containing the authenticated user's details
   */
  @PostMapping("/login")
  @RateLimit(limit = 5, duration = 10, unit = TimeUnit.MINUTES)
  public ResponseEntity<UserResponse> login(
      @Valid @RequestBody AuthenticationRequest request, HttpServletResponse response) {

    return ResponseEntity.ok(authenticationService.login(request, response));
  }

  /**
   * Logs out the currently authenticated user.
   *
   * @param authentication the authentication object containing user details
   * @param response the HTTP response to clear cookies if needed
   * @return ResponseEntity with no content
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(Authentication authentication, HttpServletResponse response) {

    User currentUser = (User) authentication.getPrincipal();
    authenticationService.logout(currentUser, response);

    return ResponseEntity.ok().build();
  }

  /**
   * Refreshes the JWT token using the refresh token from cookies.
   *
   * @param request the HTTP request containing cookies
   * @param response the HTTP response to set new cookies if needed
   * @return ResponseEntity containing the new access and refresh tokens
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
