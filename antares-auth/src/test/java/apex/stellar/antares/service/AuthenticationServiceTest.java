package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.dto.TokenRefreshResponse;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.AccountLockedException;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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

  private final String accessToken = "access_token";
  private final String refreshToken = "refresh_token";
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

  @BeforeEach
  void setUp() {
    lenient().when(jwtService.getAccessTokenCookieName()).thenReturn(accessToken);
    lenient().when(jwtService.getRefreshTokenCookieName()).thenReturn(refreshToken);
  }

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
              assertEquals(PlatformRole.USER, user.getPlatformRole());
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
  @DisplayName("refreshToken: should issue new tokens when refresh token is valid")
  void testRefreshToken_Success() {
    String oldToken = "valid-refresh-token";
    User user = new User();

    when(refreshTokenService.findUserByToken(oldToken)).thenReturn(Optional.of(user));
    when(jwtService.generateToken(user)).thenReturn("new-access-token");
    when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh-token");

    TokenRefreshResponse response =
        authenticationService.refreshToken(oldToken, httpServletResponse);

    assertNotNull(response);
    assertEquals("new-access-token", response.accessToken());
    verify(cookieService)
        .addCookie(eq(accessToken), eq("new-access-token"), anyLong(), eq(httpServletResponse));
  }

  @Test
  @DisplayName("refreshToken: should throw NotFound if token invalid")
  void testRefreshToken_NotFound() {
    String oldToken = "invalid-token";
    when(refreshTokenService.findUserByToken(oldToken)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> authenticationService.refreshToken(oldToken, httpServletResponse));
  }

  @Test
  @DisplayName("logout: should revoke refresh token and clear cookies")
  void testLogout_shouldRevokeTokenAndClearCookies() {
    // Given
    User currentUser = new User();

    // When
    authenticationService.logout(currentUser, httpServletResponse);

    // Then
    verify(refreshTokenService).deleteTokenForUser(currentUser);
    verify(cookieService).clearCookie(accessToken, httpServletResponse);
    verify(cookieService).clearCookie(refreshToken, httpServletResponse);
  }

  @Test
  @DisplayName("impersonate: should issue tokens for target user")
  void testImpersonate_Success() {
    Long targetId = 99L;
    User targetUser = new User();
    targetUser.setId(targetId);

    when(userRepository.findById(targetId)).thenReturn(Optional.of(targetUser));
    when(jwtService.generateToken(targetUser)).thenReturn("admin-impersonation-token");
    when(refreshTokenService.createRefreshToken(targetUser)).thenReturn("admin-refresh-token");
    when(userMapper.toUserResponse(targetUser)).thenReturn(mock(UserResponse.class));

    authenticationService.impersonate(targetId, httpServletResponse);

    verify(cookieService)
        .addCookie(
            eq(accessToken), eq("admin-impersonation-token"), anyLong(), eq(httpServletResponse));
  }
}
