package apex.stellar.antares.service;

import apex.stellar.antares.config.SupportSessionAuthentication;
import apex.stellar.antares.dto.SupportActor;
import apex.stellar.antares.dto.SupportSessionInfo;
import apex.stellar.antares.dto.SupportSessionRequest;
import apex.stellar.antares.dto.SupportSessionResponse;
import apex.stellar.antares.exception.DataConflictException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.SupportSession;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.SupportSessionRepository;
import apex.stellar.antares.repository.jpa.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletResponse;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audited admin support sessions: a platform ADMIN temporarily acts as a real (non-admin) user, for
 * instance to reproduce a bug reported by a gym.
 *
 * <p>A session lasts {@link #DURATION} and is never refreshed. Its token lives in the HttpOnly
 * cookie {@value #COOKIE_NAME} next to the admin's own access/refresh cookies, which stay
 * untouched. It is honoured only together with a valid access token of the same admin (see {@link
 * #resolve}); any mismatch, expiry or end makes the request fail rather than fall back to the
 * admin's rights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportSessionService {

  /** Name of the cookie holding the support token. */
  public static final String COOKIE_NAME = "support_token";

  /** Fixed lifetime of a support session. */
  public static final Duration DURATION = Duration.ofMinutes(30);

  private final SupportSessionRepository supportSessionRepository;
  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final JwtDecoder jwtDecoder;
  private final CookieService cookieService;
  private final UserMapper userMapper;
  private final Clock clock;

  /** Number of recent targets offered on the admin support page. */
  public static final int RECENT_TARGETS = 5;

  /**
   * Lists the users an admin acted as most recently (one entry per user, latest first).
   *
   * @param admin the platform admin.
   * @return up to {@value #RECENT_TARGETS} target user ids.
   */
  @Transactional(readOnly = true)
  public List<Long> recentTargetIds(User admin) {
    return supportSessionRepository.findRecentTargetIds(
        admin.getId(), PageRequest.of(0, RECENT_TARGETS));
  }

  /**
   * Starts a support session and sets its cookie.
   *
   * @param admin the platform admin starting the session.
   * @param request the target user and the reason.
   * @param response the HTTP response receiving the cookie.
   * @return the started session.
   * @throws ResourceNotFoundException if the target user does not exist.
   * @throws AccessDeniedException if the target is a platform ADMIN.
   * @throws DataConflictException if the admin already has an active session.
   */
  @Transactional
  public SupportSessionResponse start(
      User admin, SupportSessionRequest request, HttpServletResponse response) {
    User target =
        userRepository
            .findById(request.userId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.user.not.found", request.userId()));

    if (target.getPlatformRole() == PlatformRole.ADMIN) {
      throw new AccessDeniedException("error.support.session.target.admin");
    }

    Instant now = clock.instant();
    if (!supportSessionRepository.findActiveByAdminId(admin.getId(), now).isEmpty()) {
      throw new DataConflictException("error.support.session.active");
    }

    SupportSession session =
        supportSessionRepository.save(
            SupportSession.builder()
                .adminId(admin.getId())
                .targetUserId(target.getId())
                .reason(request.reason().trim())
                .startedAt(now)
                .expiresAt(now.plus(DURATION))
                .build());

    String token =
        jwtService.generateSupportToken(
            target.getId(),
            admin.getId(),
            session.getId(),
            session.getStartedAt(),
            session.getExpiresAt());
    cookieService.addCookie(COOKIE_NAME, token, DURATION.toMillis(), response);

    log.info(
        "Support session started: sessionId={}, adminId={}, targetUserId={}, reason='{}'",
        session.getId(),
        admin.getId(),
        target.getId(),
        sanitize(session.getReason()));

    return new SupportSessionResponse(
        session.getId(),
        toActor(admin),
        userMapper.toSummary(target),
        session.getReason(),
        session.getStartedAt(),
        session.getExpiresAt());
  }

  /**
   * Resolves the identity of a request carrying a support token.
   *
   * <p>The token is honoured only if it is a valid support token, its {@code act} claim is the
   * admin authenticated by the access token, and the persisted session is still active and points
   * to an existing non-admin user. Otherwise, the session (if it belongs to this admin) is marked
   * ended and an empty result is returned: the caller must reject the request.
   *
   * @param token the raw support token from the cookie.
   * @param actor the user authenticated by the access token.
   * @return the support authentication, or empty when the session is not usable.
   */
  @Transactional
  public Optional<SupportSessionAuthentication> resolve(String token, User actor) {
    Instant now = clock.instant();
    Jwt jwt;
    try {
      jwt = jwtDecoder.decode(token);
    } catch (JwtException e) {
      // Typically expired: end the session it refers to, if it is the actor's
      endUnverified(token, actor, now);
      return Optional.empty();
    }

    Long sessionId = asLong(jwt.getClaim(JwtService.CLAIM_SESSION_ID));
    Long actorId = asLong(jwt.getClaim(JwtService.CLAIM_ACTOR));
    if (!JwtService.SUPPORT_TOKEN_TYPE.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))
        || sessionId == null
        || !Objects.equals(actorId, actor.getId())) {
      log.warn("Support token rejected: not a support token of admin {}", actor.getId());
      return Optional.empty();
    }

    Optional<SupportSession> found =
        supportSessionRepository
            .findById(sessionId)
            .filter(s -> Objects.equals(s.getAdminId(), actor.getId()));
    if (found.isEmpty()) {
      log.warn("Support token rejected: session {} is not admin {}'s", sessionId, actor.getId());
      return Optional.empty();
    }
    SupportSession session = found.get();

    Optional<User> target =
        actor.getPlatformRole() == PlatformRole.ADMIN
                && session.isActive(now)
                && String.valueOf(session.getTargetUserId()).equals(jwt.getSubject())
            ? userRepository
                .findById(session.getTargetUserId())
                .filter(u -> u.getPlatformRole() != PlatformRole.ADMIN)
            : Optional.empty();

    if (target.isEmpty()) {
      endAndLog(session, now, "expired");
      return Optional.empty();
    }
    return Optional.of(new SupportSessionAuthentication(target.get(), actor, session));
  }

  /**
   * Ends the admin's active support session(s), if any, and clears the support cookie. Idempotent.
   *
   * @param admin the admin (authenticated by the access token).
   * @param response the HTTP response receiving the cleared cookie.
   */
  @Transactional
  public void endActiveSessions(User admin, HttpServletResponse response) {
    Instant now = clock.instant();
    supportSessionRepository
        .findActiveByAdminId(admin.getId(), now)
        .forEach(session -> endAndLog(session, now, "ended"));
    cookieService.clearCookie(COOKIE_NAME, response);
  }

  /**
   * Describes the support session behind a request, for {@code /users/me}.
   *
   * @param authentication the support authentication.
   * @return the session info.
   */
  public SupportSessionInfo toInfo(SupportSessionAuthentication authentication) {
    SupportSession session = authentication.getSession();
    return new SupportSessionInfo(
        session.getId(),
        toActor(authentication.getActor()),
        session.getReason(),
        session.getExpiresAt());
  }

  // --- Helpers ---

  /**
   * Ends the session referenced by a token that failed validation (e.g. expired). The claims are
   * read without verification, so only a session that the persisted record assigns to this same
   * actor is touched.
   */
  private void endUnverified(String token, User actor, Instant now) {
    Long sessionId;
    try {
      JWTClaimsSet claims = JWTParser.parse(token).getJWTClaimsSet();
      sessionId = asLong(claims.getClaim(JwtService.CLAIM_SESSION_ID));
    } catch (ParseException | RuntimeException e) {
      log.warn("Support token rejected: unreadable token (adminId={})", actor.getId());
      return;
    }
    if (sessionId == null) {
      return;
    }
    supportSessionRepository
        .findById(sessionId)
        .filter(s -> Objects.equals(s.getAdminId(), actor.getId()))
        .ifPresent(s -> endAndLog(s, now, "expired"));
  }

  private void endAndLog(SupportSession session, Instant now, String cause) {
    if (session.end(now)) {
      supportSessionRepository.save(session);
      log.info(
          "Support session {}: sessionId={}, adminId={}, targetUserId={}, reason='{}'",
          cause,
          session.getId(),
          session.getAdminId(),
          session.getTargetUserId(),
          sanitize(session.getReason()));
    }
  }

  private SupportActor toActor(User admin) {
    return new SupportActor(admin.getId(), admin.getFirstName(), admin.getLastName());
  }

  /** Reads a numeric claim, whatever its JSON number type. */
  private static @Nullable Long asLong(@Nullable Object claim) {
    return claim instanceof Number number ? number.longValue() : null;
  }

  /** Prevents log forging through the free-text reason. */
  private static String sanitize(String value) {
    return value.replaceAll("[\\r\\n]", " ");
  }
}
