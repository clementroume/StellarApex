package apex.stellar.aldebaran.security;

import static apex.stellar.aldebaran.security.SecurityService.MANAGE_SETTINGS;
import static apex.stellar.aldebaran.security.SecurityService.WOD_WRITE;

import apex.stellar.aldebaran.dto.SessionRequest;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.service.SessionSettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security bean of training sessions (lot B).
 *
 * <ul>
 *   <li><b>Frame</b> (date, type, discipline, block kinds and themes): members of the session's gym
 *       (any role), the author of a personal session, admins.
 *   <li><b>Content</b> (labels, texts, WODs, leaderboards): staff of the gym (any non-athlete role,
 *       with or without a permission), the author of a personal session, admins; athletes from
 *       {@code contentVisibleFrom}, or as participants (journal) for the participant policies.
 *   <li><b>Writes</b>: WOD_WRITE in the gym; personal session: its author; admins.
 * </ul>
 *
 * <p>As for the other security beans, a missing resource is allowed so the service answers 404.
 */
@Component("sessionSecurity")
@RequiredArgsConstructor
public class SessionSecurity {

  private static final String ATHLETE = "ATHLETE";

  private final SecurityService securityService;
  private final SessionRepository sessionRepository;
  private final SessionLogRepository sessionLogRepository;
  private final SessionBlockRepository sessionBlockRepository;
  private final SessionSettingsService settingsService;
  private final Clock clock;

  /**
   * Evaluates if the user reads the frame of the session.
   *
   * @param sessionId The session.
   * @param principal The authenticated user.
   * @return {@code true} if allowed or if the session does not exist.
   */
  @Transactional(readOnly = true)
  public boolean canRead(Long sessionId, AldebaranUserPrincipal principal) {
    return sessionRepository.findById(sessionId).map(s -> canReadFrame(s, principal)).orElse(true);
  }

  /**
   * Evaluates if the user reads the frame of an already loaded session.
   *
   * @param session The session.
   * @param principal The authenticated user.
   * @return {@code true} if allowed.
   */
  public boolean canReadFrame(Session session, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }
    return session.getGymId() == null
        ? Objects.equals(session.getAuthorId(), principal.getId())
        : principal.isMemberOf(session.getGymId());
  }

  /**
   * Evaluates if the user reads the content of a session whose frame they read.
   *
   * @param session The session.
   * @param principal The authenticated user.
   * @param participant Whether the user logged the session.
   * @return {@code true} if the content is shown to the user.
   */
  public boolean isContentVisible(
      Session session, AldebaranUserPrincipal principal, boolean participant) {
    if (!canReadFrame(session, principal)) {
      return false;
    }
    if (securityService.isAdmin(principal)
        || session.getGymId() == null
        || isStaff(principal, session.getGymId())) {
      return true;
    }
    if (participant
        && session.getVisibilityPolicy() != null
        && session.getVisibilityPolicy().opensToParticipants()) {
      return true;
    }
    Instant from = session.getContentVisibleFrom();
    return from != null && !clock.instant().isBefore(from);
  }

  /**
   * Evaluates if the user reads the content of the session owning a session WOD (WOD detail and
   * leaderboard of a block WOD).
   *
   * @param wodId A WOD with {@code standalone = false}.
   * @param principal The authenticated user.
   * @return {@code true} if the content of its session is shown to the user.
   */
  @Transactional(readOnly = true)
  public boolean canReadWodContent(Long wodId, AldebaranUserPrincipal principal) {
    return sessionBlockRepository
        .findSessionOwningWod(wodId)
        .map(
            s ->
                isContentVisible(
                    s,
                    principal,
                    sessionLogRepository.existsByUserIdAndSessionId(principal.getId(), s.getId())))
        .orElse(false);
  }

  /**
   * Evaluates if the user reads the content of a session (its scores, e.g. the session leaderboard
   * of a borrowed WOD). No session given means no restriction.
   *
   * @param sessionId The session, or null.
   * @param principal The authenticated user.
   * @return {@code true} if allowed, if no session is given or if it does not exist.
   */
  @Transactional(readOnly = true)
  public boolean canReadContent(Long sessionId, AldebaranUserPrincipal principal) {
    if (sessionId == null) {
      return true;
    }
    return sessionRepository
        .findById(sessionId)
        .map(
            s ->
                isContentVisible(
                    s,
                    principal,
                    sessionLogRepository.existsByUserIdAndSessionId(principal.getId(), s.getId())))
        .orElse(true);
  }

  /**
   * Evaluates if the user may create the session: WOD_WRITE in its gym, anyone for a personal
   * session.
   *
   * @param request The session.
   * @param principal The authenticated user.
   * @return {@code true} if allowed.
   */
  public boolean canCreate(SessionRequest request, AldebaranUserPrincipal principal) {
    return securityService.isAdmin(principal)
        || request.gymId() == null
        || securityService.hasGymPermission(principal, request.gymId(), WOD_WRITE);
  }

  /**
   * Evaluates if the user may update or delete the session, or change its visibility.
   *
   * @param sessionId The session.
   * @param principal The authenticated user.
   * @return {@code true} if allowed or if the session does not exist.
   */
  @Transactional(readOnly = true)
  public boolean canModify(Long sessionId, AldebaranUserPrincipal principal) {
    return sessionRepository.findById(sessionId).map(s -> canModify(s, principal)).orElse(true);
  }

  /**
   * Evaluates if the user may modify an already loaded session.
   *
   * @param session The session.
   * @param principal The authenticated user.
   * @return {@code true} for WOD_WRITE in its gym, the author of a personal session or an admin.
   */
  public boolean canModify(Session session, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }
    return session.getGymId() == null
        ? Objects.equals(session.getAuthorId(), principal.getId())
        : securityService.hasGymPermission(principal, session.getGymId(), WOD_WRITE);
  }

  /**
   * Evaluates if the user may log the session now ("Séance faite"): frame readable and the
   * session's first day reached in the gym's time zone.
   *
   * @param session The session.
   * @param principal The authenticated user.
   * @return {@code true} if allowed.
   */
  public boolean canLogNow(Session session, AldebaranUserPrincipal principal) {
    return canReadFrame(session, principal) && !today(session).isBefore(session.getDate());
  }

  /**
   * Today in the time zone of the session's gym.
   *
   * @param session The session.
   * @return The local day.
   */
  public LocalDate today(Session session) {
    return LocalDate.now(clock.withZone(settingsService.zoneOf(session.getGymId())));
  }

  /**
   * Evaluates if the user manages the journal: its athlete or an admin.
   *
   * @param logId The journal.
   * @param principal The authenticated user.
   * @return {@code true} if allowed or if the journal does not exist.
   */
  @Transactional(readOnly = true)
  public boolean canManageLog(Long logId, AldebaranUserPrincipal principal) {
    if (securityService.isAdmin(principal)) {
      return true;
    }
    return sessionLogRepository
        .findById(logId)
        .map(l -> Objects.equals(l.getUserId(), principal.getId()))
        .orElse(true);
  }

  /**
   * Evaluates if the user reads the session settings of the gym: its members.
   *
   * @param gymId The gym.
   * @param principal The authenticated user.
   * @return {@code true} if allowed.
   */
  public boolean canReadSettings(Long gymId, AldebaranUserPrincipal principal) {
    return securityService.isAdmin(principal) || principal.isMemberOf(gymId);
  }

  /**
   * Evaluates if the user changes the session settings of the gym: MANAGE_SETTINGS.
   *
   * @param gymId The gym.
   * @param principal The authenticated user.
   * @return {@code true} if allowed.
   */
  public boolean canWriteSettings(Long gymId, AldebaranUserPrincipal principal) {
    return securityService.isAdmin(principal)
        || securityService.hasGymPermission(principal, gymId, MANAGE_SETTINGS);
  }

  /**
   * Staff of the gym: any role but athlete (Owner, Programmer, Coach), permissions aside.
   *
   * @param principal The authenticated user.
   * @param gymId The gym.
   * @return {@code true} for staff.
   */
  public boolean isStaff(AldebaranUserPrincipal principal, Long gymId) {
    GymAccess access = principal.getMemberships() == null ? null : principal.getMemberships().get(gymId);
    return access != null && !ATHLETE.equals(access.role());
  }
}
