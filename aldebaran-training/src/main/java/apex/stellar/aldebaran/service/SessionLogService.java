package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.dto.SessionLogRequest;
import apex.stellar.aldebaran.dto.SessionLogResponse;
import apex.stellar.aldebaran.dto.SessionLogResponse.BlockDone;
import apex.stellar.aldebaran.exception.DataConflictException;
import apex.stellar.aldebaran.exception.InvalidSessionException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.ScoreMapper;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.SessionLog;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.security.SessionSecurity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal of the sessions done by the athletes (D13): "Séance faite" creates the journal with every
 * block checked; the athlete unchecks what they skipped and logs the scores of the scored blocks.
 * A journal makes its athlete a participant and locks the session.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionLogService {

  private static final String ERROR_SESSION_NOT_FOUND = "error.session.not.found";
  private static final String ERROR_LOG_NOT_FOUND = "error.session.log.not.found";

  private final SessionRepository sessionRepository;
  private final SessionLogRepository sessionLogRepository;
  private final ScoreRepository scoreRepository;
  private final ScoreMapper scoreMapper;
  private final ScoreCapabilityService scoreCapabilityService;
  private final SessionSecurity sessionSecurity;
  private final SecurityService securityService;

  /**
   * "Séance faite": logs the session for the current user, every block checked. Logging again
   * returns the existing journal.
   *
   * @param sessionId The session.
   * @param request The day done (today by default) and, for a personal session, the gym.
   * @param principal The current user.
   * @return The journal.
   * @throws InvalidSessionException if the day is outside the session's day or week, or in the
   *     future.
   */
  @Transactional
  public SessionLogResponse logSession(
      Long sessionId, SessionLogRequest request, AldebaranUserPrincipal principal) {
    Session session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND, sessionId));
    LocalDate date =
        request != null && request.date() != null ? request.date() : sessionSecurity.today(session);
    Long gymId = session.getGymId();
    if (gymId == null && request != null && request.gymId() != null) {
      if (!securityService.isAdmin(principal) && !principal.isMemberOf(request.gymId())) {
        throw new AccessDeniedException("Not a member of gym " + request.gymId());
      }
      gymId = request.gymId();
    }
    SessionLog journal = findOrCreate(session, principal.getId(), date, gymId, principal.getId());
    return toResponse(journal, principal);
  }

  /**
   * Retrieves the athlete's journal of the session, or creates it with every block checked (a
   * score logged on a session WOD, or by a judge for the athlete, implies the session was done).
   *
   * @param session The session.
   * @param userId The athlete.
   * @param date The day the session was done.
   * @param gymId The gym where it was done.
   * @param loggedBy The user creating the journal.
   * @return The journal.
   * @throws InvalidSessionException if a new journal's day is outside the session or in the future.
   */
  @Transactional
  public SessionLog findOrCreate(
      Session session, Long userId, LocalDate date, Long gymId, Long loggedBy) {
    return sessionLogRepository
        .findByUserIdAndSessionId(userId, session.getId())
        .orElseGet(
            () -> {
              if (!session.covers(date) || date.isAfter(sessionSecurity.today(session))) {
                throw new InvalidSessionException(
                    "error.session.log.date", session.getDate(), session.getEndDate());
              }
              SessionLog journal =
                  SessionLog.builder()
                      .userId(userId)
                      .session(session)
                      .gymId(gymId)
                      .date(date)
                      .loggedBy(loggedBy)
                      .build();
              session.getBlocks().forEach(b -> journal.getBlocks().put(b.getId(), true));
              SessionLog saved = sessionLogRepository.save(journal);
              log.info(
                  "Session id={} logged by user id={} for user id={}",
                  session.getId(),
                  loggedBy,
                  userId);
              return saved;
            });
  }

  /**
   * Retrieves the journal of the current user, latest first.
   *
   * @param from First day, or null.
   * @param to Last day, or null.
   * @param pageable Pagination.
   * @param principal The current user.
   * @return A slice of journals with their scores.
   */
  @Transactional(readOnly = true)
  public Slice<SessionLogResponse> getMyLogs(
      LocalDate from, LocalDate to, Pageable pageable, AldebaranUserPrincipal principal) {
    Slice<SessionLog> logs =
        sessionLogRepository.findByUser(
            principal.getId(),
            from != null ? from : SessionRepository.NO_START,
            to != null ? to : SessionRepository.NO_END,
            pageable);
    Map<Long, List<Score>> scores =
        scoresByLog(logs.getContent().stream().map(SessionLog::getId).toList());
    return logs.map(l -> toResponse(l, scores.getOrDefault(l.getId(), List.of()), principal));
  }

  /**
   * Checks or unchecks a block of a journal.
   *
   * @param logId The journal.
   * @param blockId The block (of the journal's session).
   * @param done Whether the athlete did it.
   * @param principal The current user.
   * @return The journal.
   * @throws ResourceNotFoundException if the journal does not exist or the block is not one of its
   *     session's.
   */
  @Transactional
  public SessionLogResponse setBlockDone(
      Long logId, Long blockId, boolean done, AldebaranUserPrincipal principal) {
    SessionLog journal = findLog(logId);
    boolean inSession =
        journal.getSession().getBlocks().stream().anyMatch(b -> b.getId().equals(blockId));
    if (!inSession) {
      throw new ResourceNotFoundException("error.session.block.not.found", blockId);
    }
    journal.getBlocks().put(blockId, done);
    return toResponse(sessionLogRepository.save(journal), principal);
  }

  /**
   * Deletes a journal (the athlete is no longer a participant).
   *
   * @param logId The journal.
   * @throws DataConflictException if scores are linked to it (delete them first).
   */
  @Transactional
  public void deleteLog(Long logId) {
    SessionLog journal = findLog(logId);
    if (scoreRepository.existsBySessionLogId(logId)) {
      throw new DataConflictException("error.session.log.has.scores", logId);
    }
    sessionLogRepository.delete(journal);
  }

  /**
   * Maps a journal with its scores, as seen by the user.
   *
   * @param journal The journal.
   * @param principal The viewer.
   * @return The response.
   */
  @Transactional(readOnly = true)
  public SessionLogResponse toResponse(SessionLog journal, AldebaranUserPrincipal principal) {
    return toResponse(
        journal, scoresByLog(List.of(journal.getId())).getOrDefault(journal.getId(), List.of()), principal);
  }

  private SessionLogResponse toResponse(
      SessionLog journal, List<Score> scores, AldebaranUserPrincipal principal) {
    Session session = journal.getSession();
    List<BlockDone> blocks =
        session.getBlocks().stream()
            .map(
                (SessionBlock b) ->
                    new BlockDone(
                        b.getId(),
                        b.getKind(),
                        Boolean.TRUE.equals(journal.getBlocks().getOrDefault(b.getId(), false))))
            .toList();
    List<ScoreResponse> scoreResponses =
        scores.stream()
            .map(scoreMapper::toResponse)
            .map(s -> scoreCapabilityService.forViewer(s, principal))
            .toList();
    return new SessionLogResponse(
        journal.getId(),
        journal.getUserId(),
        session.getId(),
        session.getType(),
        session.getDiscipline(),
        session.getDate(),
        session.getTitle(),
        journal.getGymId(),
        journal.getDate(),
        journal.getSource(),
        blocks,
        scoreResponses);
  }

  private Map<Long, List<Score>> scoresByLog(Collection<Long> logIds) {
    if (logIds.isEmpty()) {
      return Map.of();
    }
    return scoreRepository.findBySessionLogIdIn(logIds).stream()
        .collect(Collectors.groupingBy(Score::getSessionLogId));
  }

  private SessionLog findLog(Long logId) {
    return sessionLogRepository
        .findById(logId)
        .orElseThrow(() -> new ResourceNotFoundException(ERROR_LOG_NOT_FOUND, logId));
  }
}
