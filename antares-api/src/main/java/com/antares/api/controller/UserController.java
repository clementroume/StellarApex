package com.antares.api.controller;

import com.antares.api.dto.ChangePasswordRequest;
import com.antares.api.dto.PreferencesUpdateRequest;
import com.antares.api.dto.ProfileUpdateRequest;
import com.antares.api.dto.UserResponse;
import com.antares.api.mapper.UserMapper;
import com.antares.api.model.User;
import com.antares.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final UserMapper userMapper;

  /**
   * Handles GET requests to retrieve the profile of the currently authenticated user. The user is
   * identified via the Authentication principal injected by Spring Security.
   *
   * @param authentication The authentication object containing the user's principal.
   * @return A ResponseEntity containing the {@link UserResponse} for the current user.
   */
  @GetMapping("/me")
  public ResponseEntity<UserResponse> getAuthenticatedUser(Authentication authentication) {

    User currentUser = (User) authentication.getPrincipal();

    return ResponseEntity.ok(userMapper.toUserResponse(currentUser));
  }

  /**
   * Handles PUT requests to update the authenticated user's core profile information.
   *
   * @param request The DTO with the updated user data, which is validated.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PutMapping("/me/profile")
  public ResponseEntity<UserResponse> updateProfile(
      @Valid @RequestBody ProfileUpdateRequest request, Authentication authentication) {

    User currentUser = (User) authentication.getPrincipal();

    return ResponseEntity.ok(userService.updateProfile(currentUser, request));
  }

  /**
   * Handles PATCH requests to partially update the authenticated user's preferences.
   *
   * @param request The DTO with the updated preferences data, which is validated.
   * @param authentication The current user's authentication principal.
   * @return A ResponseEntity containing the updated {@link UserResponse}.
   */
  @PatchMapping("/me/preferences")
  public ResponseEntity<UserResponse> updatePreferences(
      @Valid @RequestBody PreferencesUpdateRequest request, Authentication authentication) {

    User currentUser = (User) authentication.getPrincipal();

    return ResponseEntity.ok(userService.updatePreferences(currentUser, request));
  }

  /**
   * Handles PUT requests to change the authenticated user's password.
   *
   * @param request The DTO with the current, new, and confirmation passwords, validated.
   * @param authentication The current user's authentication principal.
   * @return An empty ResponseEntity (200 OK) confirming success.
   */
  @PutMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {

    User currentUser = (User) authentication.getPrincipal();
    userService.changePassword(request, currentUser);

    return ResponseEntity.ok().build();
  }
}
