package com.antares.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antares.api.dto.AuthenticationRequest;
import com.antares.api.dto.RegisterRequest;
import com.antares.api.exception.DataConflictException;
import com.antares.api.mapper.UserMapper;
import com.antares.api.model.Role;
import com.antares.api.model.User;
import com.antares.api.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthenticationService}.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private UserMapper userMapper;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private CookieService cookieService;
  @Mock private HttpServletResponse httpServletResponse;

  @InjectMocks private AuthenticationService authenticationService;

  @Test
  @DisplayName("register should create user with USER role and set cookies")
  void testRegister_shouldSucceed() {
    // Given: A registration request with a new email.
    RegisterRequest request =
        new RegisterRequest("John", "Doe", "john.doe@example.com", "password123");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
    when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              assertEquals(Role.ROLE_USER, user.getRole());
              return user;
            });
    when(jwtService.generateToken(any(User.class))).thenReturn("fakeAccessToken");
    when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("fakeRefreshToken");

    // When: Registering the user.
    authenticationService.register(request, httpServletResponse);

    // Then: The user is saved and cookies are set.
    verify(userRepository).save(any(User.class));
    verify(cookieService)
        .addCookie(
            eq(jwtService.getAccessTokenCookieName()),
            eq("fakeAccessToken"),
            anyLong(),
            eq(httpServletResponse));
    verify(cookieService)
        .addCookie(
            eq(jwtService.getRefreshTokenCookieName()),
            eq("fakeRefreshToken"),
            anyLong(),
            eq(httpServletResponse));
  }

  @Test
  @DisplayName("register should throw DataConflictException when email already exists")
  void testRegister_whenEmailExists_shouldThrowException() {
    // Given: A registration request with an existing email.
    RegisterRequest request =
        new RegisterRequest("Jane", "Doe", "jane.doe@example.com", "password123");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(new User()));

    // When & Then: Registering throws DataConflictException and no user is saved or cookies set.
    assertThrows(
        DataConflictException.class,
        () -> authenticationService.register(request, httpServletResponse));

    verify(userRepository, never()).save(any(User.class));
    verify(cookieService, never())
        .addCookie(anyString(), anyString(), anyLong(), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("login should authenticate and set cookies for valid credentials")
  void testLogin_withValidCredentials_shouldAuthenticate() {
    // Given: A valid authentication request and existing user.
    AuthenticationRequest request = new AuthenticationRequest("test@example.com", "password");
    User user = User.builder().email(request.email()).build();
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
    when(jwtService.generateToken(any(User.class))).thenReturn("fakeAccessToken");
    when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("fakeRefreshToken");

    // When: Logging in with valid credentials.
    authenticationService.login(request, httpServletResponse);

    // Then: Authentication is performed and cookies are set.
    verify(authenticationManager).authenticate(any());
    verify(cookieService)
        .addCookie(
            eq(jwtService.getAccessTokenCookieName()),
            eq("fakeAccessToken"),
            anyLong(),
            eq(httpServletResponse));
    verify(cookieService)
        .addCookie(
            eq(jwtService.getRefreshTokenCookieName()),
            eq("fakeRefreshToken"),
            anyLong(),
            eq(httpServletResponse));
  }

  @Test
  @DisplayName("logout should revoke refresh token and clear cookies")
  void testLogout_shouldRevokeTokenAndClearCookies() {
    // Given: A logged-in user and cookie names.
    User currentUser = new User();
    String accessTokenName = "access_token_cookie";
    String refreshTokenName = "refresh_token_cookie";
    when(jwtService.getAccessTokenCookieName()).thenReturn(accessTokenName);
    when(jwtService.getRefreshTokenCookieName()).thenReturn(refreshTokenName);

    // When: Logging out the user.
    authenticationService.logout(currentUser, httpServletResponse);

    // Then: The refresh token is revoked and cookies are cleared.
    verify(refreshTokenService).deleteTokenForUser(currentUser);
    verify(cookieService).clearCookie(accessTokenName, httpServletResponse);
    verify(cookieService).clearCookie(refreshTokenName, httpServletResponse);
    verifyNoMoreInteractions(cookieService);
  }
}
