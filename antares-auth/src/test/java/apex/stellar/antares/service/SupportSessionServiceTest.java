package apex.stellar.antares.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import apex.stellar.antares.config.SupportSessionAuthentication;
import apex.stellar.antares.dto.SupportSessionRequest;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.SupportSession;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.SupportSessionRepository;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@ExtendWith(MockitoExtension.class)
class SupportSessionServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

  @Mock private SupportSessionRepository supportSessionRepository;
  @Mock private UserRepository userRepository;
  @Mock private JwtService jwtService;
  @Mock private JwtDecoder jwtDecoder;
  @Mock private CookieService cookieService;
  @Mock private UserMapper userMapper;

  private SupportSessionService service;
  private User admin;
  private User target;
  private SupportSession session;

  @BeforeEach
  void setUp() {
    service =
        new SupportSessionService(
            supportSessionRepository,
            userRepository,
            jwtService,
            jwtDecoder,
            cookieService,
            userMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
    admin = User.builder().id(1L).email("admin@test.com").platformRole(PlatformRole.ADMIN).build();
    target = User.builder().id(2L).email("user@test.com").platformRole(PlatformRole.USER).build();
    session =
        SupportSession.builder()
            .id(7L)
            .adminId(1L)
            .targetUserId(2L)
            .reason("Ticket")
            .startedAt(NOW.minusSeconds(60))
            .expiresAt(NOW.plusSeconds(600))
            .build();
  }

  private Jwt supportJwt(Object actorId, Object sessionId, String type) {
    return new Jwt(
        "token",
        NOW.minusSeconds(60),
        NOW.plusSeconds(600),
        Map.of("alg", "HS256"),
        Map.of(
            "sub", "2",
            JwtService.CLAIM_ACTOR, actorId,
            JwtService.CLAIM_SESSION_ID, sessionId,
            JwtService.CLAIM_TYPE, type));
  }

  // --- start ---

  @Test
  @DisplayName("start: Admin target should be refused")
  void start_AdminTarget_ShouldBeRefused() {
    User otherAdmin = User.builder().id(3L).platformRole(PlatformRole.ADMIN).build();
    when(userRepository.findById(3L)).thenReturn(Optional.of(otherAdmin));

    assertThrows(
        AccessDeniedException.class,
        () ->
            service.start(
                admin, new SupportSessionRequest(3L, "Why"), new MockHttpServletResponse()));
    verify(supportSessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("start: An active session should be a conflict")
  void start_ActiveSession_ShouldConflict() {
    when(userRepository.findById(2L)).thenReturn(Optional.of(target));
    when(supportSessionRepository.findActiveByAdminId(1L, NOW)).thenReturn(List.of(session));

    assertThrows(
        DataConflictException.class,
        () ->
            service.start(
                admin, new SupportSessionRequest(2L, "Why"), new MockHttpServletResponse()));
  }

  @Test
  @DisplayName("start: Should persist a 30 minute session and set the cookie")
  void start_ShouldPersistAndSetCookie() {
    when(userRepository.findById(2L)).thenReturn(Optional.of(target));
    when(supportSessionRepository.findActiveByAdminId(1L, NOW)).thenReturn(List.of());
    when(supportSessionRepository.save(any()))
        .thenAnswer(
            i -> {
              SupportSession s = i.getArgument(0);
              s.setId(7L);
              return s;
            });
    when(jwtService.generateSupportToken(2L, 1L, 7L, NOW, NOW.plusSeconds(1800)))
        .thenReturn("support-jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();

    var result = service.start(admin, new SupportSessionRequest(2L, " Ticket #1 "), response);

    assertEquals(7L, result.id());
    assertEquals("Ticket #1", result.reason());
    assertEquals(NOW.plusSeconds(1800), result.expiresAt());
    verify(cookieService)
        .addCookie(SupportSessionService.COOKIE_NAME, "support-jwt", 1_800_000L, response);
  }

  // --- resolve ---

  @Test
  @DisplayName("resolve: Valid token of the same admin should act as the target")
  void resolve_Valid_ShouldReturnTarget() {
    when(jwtDecoder.decode("token")).thenReturn(supportJwt(1L, 7L, "support"));
    when(supportSessionRepository.findById(7L)).thenReturn(Optional.of(session));
    when(userRepository.findById(2L)).thenReturn(Optional.of(target));

    Optional<SupportSessionAuthentication> result = service.resolve("token", admin);

    assertTrue(result.isPresent());
    assertSame(target, result.get().getPrincipal());
    assertSame(admin, result.get().getActor());
  }

  @Test
  @DisplayName("resolve: Token of another admin should be rejected without ending the session")
  void resolve_OtherAdmin_ShouldBeRejected() {
    when(jwtDecoder.decode("token")).thenReturn(supportJwt(5L, 7L, "support"));

    assertTrue(service.resolve("token", admin).isEmpty());
    assertNull(session.getEndedAt());
  }

  @Test
  @DisplayName("resolve: A token of another type should be rejected")
  void resolve_WrongType_ShouldBeRejected() {
    when(jwtDecoder.decode("token")).thenReturn(supportJwt(1L, 7L, "access"));

    assertTrue(service.resolve("token", admin).isEmpty());
  }

  @Test
  @DisplayName("resolve: Expired session should be rejected and marked ended")
  void resolve_ExpiredSession_ShouldEndIt() {
    session.setExpiresAt(NOW.minusSeconds(1));
    when(jwtDecoder.decode("token")).thenReturn(supportJwt(1L, 7L, "support"));
    when(supportSessionRepository.findById(7L)).thenReturn(Optional.of(session));

    assertTrue(service.resolve("token", admin).isEmpty());
    assertEquals(NOW.minusSeconds(1), session.getEndedAt());
    verify(supportSessionRepository).save(session);
  }

  @Test
  @DisplayName("resolve: Undecodable token should be rejected")
  void resolve_BadToken_ShouldBeRejected() {
    when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("bad"));

    assertTrue(service.resolve("garbage", admin).isEmpty());
    verify(supportSessionRepository, never()).save(any());
  }

  // --- end ---

  @Test
  @DisplayName("endActiveSessions: Should end active sessions and clear the cookie")
  void endActiveSessions_ShouldEndAndClear() {
    when(supportSessionRepository.findActiveByAdminId(1L, NOW)).thenReturn(List.of(session));
    MockHttpServletResponse response = new MockHttpServletResponse();

    service.endActiveSessions(admin, response);

    assertEquals(NOW, session.getEndedAt());
    verify(cookieService).clearCookie(SupportSessionService.COOKIE_NAME, response);
  }
}
