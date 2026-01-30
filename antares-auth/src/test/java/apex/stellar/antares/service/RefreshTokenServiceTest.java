package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.config.JwtProperties;
import apex.stellar.antares.model.RefreshToken;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.RefreshTokenRepository;
import apex.stellar.antares.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private JwtProperties jwtProperties;

  @InjectMocks private RefreshTokenService refreshTokenService;

  private String hashValue(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @DisplayName(
      "createRefreshToken: should delete old token, save new hashed token and return raw token")
  void createRefreshToken_shouldSaveTokenInRepository() {
    // Given
    User user = new User();
    user.setId(1L);

    JwtProperties.RefreshToken refreshTokenProps = mock(JwtProperties.RefreshToken.class);
    when(jwtProperties.refreshToken()).thenReturn(refreshTokenProps);
    when(refreshTokenProps.expiration()).thenReturn(604800000L); // 7 days

    // When
    String rawToken = refreshTokenService.createRefreshToken(user);

    // Then
    assertNotNull(rawToken);
    String hashedToken = hashValue(rawToken);

    // 1. Verify that we try to delete the old token
    verify(refreshTokenRepository).findByUserId(user.getId());

    // 2. Capture the saved object to verify its properties
    ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(tokenCaptor.capture());

    RefreshToken savedToken = tokenCaptor.getValue();
    assertEquals(hashedToken, savedToken.getId()); // ID must be the hash
    assertEquals(user.getId(), savedToken.getUserId());
    assertEquals(604800L, savedToken.getExpiration()); // 7 days in seconds
  }

  @Test
  @DisplayName("findUserByToken: should return User if token exists in repo")
  void findUserByToken_shouldReturnUser_whenTokenExists() {
    // Given
    String rawToken = UUID.randomUUID().toString();
    String hashedToken = hashValue(rawToken);
    Long userId = 1L;

    User user = new User();
    user.setId(userId);

    RefreshToken tokenEntity = RefreshToken.builder().id(hashedToken).userId(userId).build();

    when(refreshTokenRepository.findById(hashedToken)).thenReturn(Optional.of(tokenEntity));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    // When
    Optional<User> result = refreshTokenService.findUserByToken(rawToken);

    // Then
    assertTrue(result.isPresent());
    assertEquals(userId, result.get().getId());
  }

  @Test
  @DisplayName("findUserByToken: should return empty if token not in repo")
  void findUserByToken_shouldReturnEmpty_whenTokenMissing() {
    // Given
    String rawToken = UUID.randomUUID().toString();
    String hashedToken = hashValue(rawToken);

    when(refreshTokenRepository.findById(hashedToken)).thenReturn(Optional.empty());

    // When
    Optional<User> result = refreshTokenService.findUserByToken(rawToken);

    // Then
    assertTrue(result.isEmpty());
    verifyNoInteractions(userRepository);
  }

  @Test
  @DisplayName("deleteTokenForUser: should find by userId and delete")
  void deleteTokenForUser_shouldFindAndDelete() {
    // Given
    User user = new User();
    user.setId(10L);
    RefreshToken existingToken = RefreshToken.builder().id("someHash").userId(10L).build();

    when(refreshTokenRepository.findByUserId(10L)).thenReturn(Optional.of(existingToken));

    // When
    refreshTokenService.deleteTokenForUser(user);

    // Then
    verify(refreshTokenRepository).delete(existingToken);
  }

  @Test
  @DisplayName("deleteTokenForUser: should do nothing if no token found")
  void deleteTokenForUser_shouldDoNothing_whenEmpty() {
    // Given
    User user = new User();
    user.setId(20L);
    when(refreshTokenRepository.findByUserId(20L)).thenReturn(Optional.empty());

    // When
    refreshTokenService.deleteTokenForUser(user);

    // Then
    verify(refreshTokenRepository, never()).delete(any());
  }
}
