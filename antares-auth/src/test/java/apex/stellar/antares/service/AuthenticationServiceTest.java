package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.exception.AccountLockedException;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.Role;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private AuthenticationManager authenticationManager;
  @Mock private UserMapper userMapper;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private CookieService cookieService;
  @Mock private LoginAttemptService loginAttemptService; // Nouvelle dépendance
  @Mock private HttpServletResponse httpServletResponse;

  @InjectMocks private AuthenticationService authenticationService;

  @Test
  @DisplayName("register: should create user with USER role and set cookies")
  void testRegister_shouldSucceed() {
    // Given
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

    // When
    authenticationService.register(request, httpServletResponse);

    // Then
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
  @DisplayName("register: should throw DataConflictException if email already exists")
  void testRegister_whenEmailExists_shouldThrowException() {
    // Given
    RegisterRequest request =
        new RegisterRequest("Jane", "Doe", "jane.doe@example.com", "password123");
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(new User()));

    // When & Then
    assertThrows(
        DataConflictException.class,
        () -> authenticationService.register(request, httpServletResponse));

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("login: should authenticate, reset attempts and set cookies for valid credentials")
  void testLogin_withValidCredentials_shouldAuthenticate() {
    // Given
    AuthenticationRequest request = new AuthenticationRequest("test@example.com", "password");
    User user = User.builder().email(request.email()).build();

    // Mock non-blocked user
    when(loginAttemptService.isBlocked(request.email())).thenReturn(false);
    when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
    when(jwtService.generateToken(any(User.class))).thenReturn("fakeAccessToken");
    when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("fakeRefreshToken");

    // When
    authenticationService.login(request, httpServletResponse);

    // Then
    verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    // Vérifie que les tentatives sont réinitialisées
    verify(loginAttemptService).loginSucceeded(request.email());
    verify(cookieService).addCookie(any(), eq("fakeAccessToken"), anyLong(), any());
    verify(cookieService).addCookie(any(), eq("fakeRefreshToken"), anyLong(), any());
  }

  @Test
  @DisplayName("login: should throw AccountLockedException if account is blocked")
  void testLogin_whenAccountLocked_shouldThrowException() {
    // Given
    AuthenticationRequest request = new AuthenticationRequest("locked@example.com", "password");
    when(loginAttemptService.isBlocked(request.email())).thenReturn(true);
    when(loginAttemptService.getBlockTimeRemaining(request.email())).thenReturn(300L);

    // When & Then
    assertThrows(
        AccountLockedException.class,
        () -> authenticationService.login(request, httpServletResponse));

    // Vérifie qu'on n'essaie même pas de s'authentifier
    verify(authenticationManager, never()).authenticate(any());
  }

  @Test
  @DisplayName("login: should record failure if BadCredentialsException is thrown")
  void testLogin_withBadCredentials_shouldRecordFailure() {
    // Given
    AuthenticationRequest request = new AuthenticationRequest("hacker@example.com", "wrong");
    when(loginAttemptService.isBlocked(request.email())).thenReturn(false);
    doThrow(new BadCredentialsException("Bad creds"))
        .when(authenticationManager)
        .authenticate(any());

    // When & Then
    assertThrows(
        BadCredentialsException.class,
        () -> authenticationService.login(request, httpServletResponse));

    // Vérifie que l'échec est enregistré
    verify(loginAttemptService).loginFailed(request.email());
    // Vérifie qu'aucun cookie n'est posé
    verify(cookieService, never()).addCookie(any(), any(), anyLong(), any());
  }

  @Test
  @DisplayName("logout: should revoke refresh token and clear cookies")
  void testLogout_shouldRevokeTokenAndClearCookies() {
    // Given
    User currentUser = new User();
    String accessTokenName = "access_token_cookie";
    String refreshTokenName = "refresh_token_cookie";
    when(jwtService.getAccessTokenCookieName()).thenReturn(accessTokenName);
    when(jwtService.getRefreshTokenCookieName()).thenReturn(refreshTokenName);

    // When
    authenticationService.logout(currentUser, httpServletResponse);

    // Then
    verify(refreshTokenService).deleteTokenForUser(currentUser);
    verify(cookieService).clearCookie(accessTokenName, httpServletResponse);
    verify(cookieService).clearCookie(refreshTokenName, httpServletResponse);
  }
}
