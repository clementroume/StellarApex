package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.SessionBlockRequest;
import apex.stellar.aldebaran.dto.SessionBlockResponse;
import apex.stellar.aldebaran.dto.SessionCapabilities;
import apex.stellar.aldebaran.dto.SessionReferenceData;
import apex.stellar.aldebaran.dto.SessionRequest;
import apex.stellar.aldebaran.dto.SessionResponse;
import apex.stellar.aldebaran.dto.SessionSummaryResponse;
import apex.stellar.aldebaran.dto.SessionSummaryResponse.BlockFrame;
import apex.stellar.aldebaran.dto.SessionVisibilityRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.exception.InvalidSessionException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.exception.WodLockedException;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.SessionBlock.Stimulus;
import apex.stellar.aldebaran.model.entities.SessionLog;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.SessionScope;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.SecurityService;
import apex.stellar.aldebaran.security.SessionSecurity;
import apex.stellar.aldebaran.security.WodSecurity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Training sessions (lot B): an ordered container of blocks proposed on a day (WOD session) or a
 * week (specialty). A scored block owns a WOD created for it ({@code standalone = false}) or borrows
 * a standalone WOD; warm-up and cool down blocks are text.
 *
 * <ul>
 *   <li>A WOD session has at least a SKILL block and a WOD block; a specialty or personal session
 *       at least one block with a WOD.
 *   <li>A session is locked (409) once someone logged it; its visibility stays editable.
 *   <li>Visibility: the policy and the local instant chosen by the staff give {@code
 *       contentVisibleFrom}, placed in time with the gym's time zone.
 * </ul>
 *
 * <p>Per-user content filtering and capabilities are computed per request (never cached).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

  private static final String ERROR_SESSION_NOT_FOUND = "error.session.not.found";

  private final SessionRepository sessionRepository;
  private final SessionLogRepository sessionLogRepository;
  private final WodRepository wodRepository;
  private final WodService wodService;
  private final WodSecurity wodSecurity;
  private final SessionSecurity sessionSecurity;
  private final SessionSettingsService settingsService;
  private final SessionLogService sessionLogService;
  private final SecurityService securityService;

  // -------------------------------------------------------------------------
  // READ
  // -------------------------------------------------------------------------

  /**
   * Retrieves the frames of the readable sessions, by date (week planning, Today page).
   *
   * @param scope ALL, MINE (personal) or GYM.
   * @param gymId Gym filter for the GYM scope, or null.
   * @param from First day, or null.
   * @param to Last day, or null.
   * @param type Session type filter, or null.
   * @param pageable Pagination.
   * @param principal The current user.
   * @return A slice of session frames.
   */
  @Transactional(readOnly = true)
  public Slice<SessionSummaryResponse> getSessions(
      SessionScope scope,
      Long gymId,
      LocalDate from,
      LocalDate to,
      SessionType type,
      Pageable pageable,
      AldebaranUserPrincipal principal) {
    Pageable sorted =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by("date").ascending().and(Sort.by("id").ascending()));
    Slice<Session> sessions =
        sessionRepository.findSecure(
            scope.name(),
            gymId,
            from != null ? from : SessionRepository.NO_START,
            to != null ? to : SessionRepository.NO_END,
            type,
            principal.getId(),
            securityService.getCurrentUserMemberGymIds(),
            securityService.isAdmin(principal),
            sorted);
    List<Long> ids = sessions.getContent().stream().map(Session::getId).toList();
    Set<Long> logged =
        ids.isEmpty()
            ? Set.of()
            : new HashSet<>(sessionLogRepository.findLoggedSessionIds(principal.getId(), ids));
    return sessions.map(s -> toSummary(s, logged.contains(s.getId()), principal));
  }

  /**
   * Retrieves a session as seen by the user: content hidden until the visibility policy opens it.
   *
   * @param id The session.
   * @param principal The current user.
   * @return The session.
   * @throws ResourceNotFoundException if the session does not exist.
   */
  @Transactional(readOnly = true)
  public SessionResponse getSession(Long id, AldebaranUserPrincipal principal) {
    return toResponse(findSession(id), principal);
  }

  /**
   * Retrieves the reference values of the session forms.
   *
   * @return The enums' names.
   */
  public SessionReferenceData getReferenceData() {
    return new SessionReferenceData(
        names(SessionType.values()),
        names(Discipline.values()),
        names(Kind.values()),
        names(Stimulus.values()),
        names(VisibilityPolicy.values()));
  }

  // -------------------------------------------------------------------------
  // WRITE
  // -------------------------------------------------------------------------

  /**
   * Creates a session with its blocks (WODs created inline, borrowed, or copied for a competition).
   *
   * @param request The session.
   * @param principal The current user (author).
   * @return The created session.
   * @throws InvalidSessionException if the composition or the visibility breaks a rule.
   */
  @Transactional
  public SessionResponse createSession(SessionRequest request, AldebaranUserPrincipal principal) {
    checkComposition(request);
    Session session =
        Session.builder()
            .gymId(request.gymId())
            .authorId(principal.getId())
            .discipline(request.discipline())
            .title(request.title())
            .descriptionEn(request.descriptionEn())
            .descriptionFr(request.descriptionFr())
            .build();
    session.schedule(request.type(), request.date());
    applyVisibility(session, request.visibilityPolicy(), request.contentVisibleAt());
    List<Long> orphans = applyBlocks(session, request.blocks(), principal);
    Session saved = sessionRepository.saveAndFlush(session);
    orphans.forEach(wodService::deleteSessionWod);
    log.info("Created session id={} (gym {}) with {} blocks", saved.getId(), saved.getGymId(),
        saved.getBlocks().size());
    return toResponse(saved, principal);
  }

  /**
   * Replaces a session: fields and blocks (merged by ID; an owned WOD left behind is deleted).
   *
   * @param id The session.
   * @param request The new definition ({@code gymId} ignored).
   * @param principal The current user.
   * @return The updated session.
   * @throws WodLockedException if someone logged the session (409).
   * @throws InvalidSessionException if the composition or the visibility breaks a rule.
   */
  @Transactional
  public SessionResponse updateSession(
      Long id, SessionRequest request, AldebaranUserPrincipal principal) {
    Session session = findSession(id);
    checkNotLocked(id);
    checkComposition(new SessionRequest(
        request.type(), request.discipline(), request.date(), request.title(),
        request.descriptionEn(), request.descriptionFr(), session.getGymId(),
        request.visibilityPolicy(), request.contentVisibleAt(), request.blocks()));
    session.setDiscipline(request.discipline());
    session.setTitle(request.title());
    session.setDescriptionEn(request.descriptionEn());
    session.setDescriptionFr(request.descriptionFr());
    session.schedule(request.type(), request.date());
    applyVisibility(
        session,
        request.visibilityPolicy() != null
            ? request.visibilityPolicy()
            : session.getVisibilityPolicy(),
        request.contentVisibleAt());
    List<Long> orphans = applyBlocks(session, request.blocks(), principal);
    Session saved = sessionRepository.saveAndFlush(session);
    orphans.forEach(wodService::deleteSessionWod);
    log.info("Updated session id={}", id);
    return toResponse(saved, principal);
  }

  /**
   * Changes the visibility of a session (allowed once locked: the staff opens the content later).
   *
   * @param id The session.
   * @param request The policy and the local opening instant.
   * @param principal The current user.
   * @return The updated session.
   */
  @Transactional
  public SessionResponse updateVisibility(
      Long id, SessionVisibilityRequest request, AldebaranUserPrincipal principal) {
    Session session = findSession(id);
    if (session.getGymId() == null) {
      throw new InvalidSessionException("error.session.visibility.personal");
    }
    applyVisibility(session, request.visibilityPolicy(), request.contentVisibleAt());
    return toResponse(sessionRepository.save(session), principal);
  }

  /**
   * Deletes a session and the WODs of its blocks (borrowed WODs stay).
   *
   * @param id The session.
   * @throws WodLockedException if someone logged the session (409).
   */
  @Transactional
  public void deleteSession(Long id) {
    Session session = findSession(id);
    checkNotLocked(id);
    List<Long> owned =
        session.getBlocks().stream()
            .map(SessionBlock::getWod)
            .filter(w -> w != null && !w.isStandalone())
            .map(Wod::getId)
            .toList();
    sessionRepository.delete(session);
    sessionRepository.flush();
    owned.forEach(wodService::deleteSessionWod);
    log.info("Deleted session id={}", id);
  }

  // -------------------------------------------------------------------------
  // RULES
  // -------------------------------------------------------------------------

  /**
   * Checks the blocks: text blocks carry no WOD, other blocks exactly one WOD source; a WOD session
   * has a SKILL and a WOD block, a specialty or personal session at least one block with a WOD.
   */
  private void checkComposition(SessionRequest request) {
    for (SessionBlockRequest block : request.blocks()) {
      boolean hasWod = block.wodId() != null || block.wod() != null;
      if (block.kind().isText()) {
        if (hasWod || block.competition()) {
          throw new InvalidSessionException("error.session.block.text.wod", block.kind());
        }
      } else if (block.wodId() != null && block.wod() != null || !hasWod) {
        throw new InvalidSessionException("error.session.block.wod.required", block.kind());
      }
    }
    Set<Kind> kinds =
        request.blocks().stream().map(SessionBlockRequest::kind).collect(Collectors.toSet());
    if (request.type() == SessionType.WOD && request.gymId() != null) {
      if (!kinds.contains(Kind.SKILL) || !kinds.contains(Kind.WOD)) {
        throw new InvalidSessionException("error.session.composition.wod");
      }
    } else if (kinds.stream().allMatch(Kind::isText)) {
      throw new InvalidSessionException("error.session.composition.any");
    }
  }

  private void checkNotLocked(Long id) {
    if (sessionLogRepository.existsBySessionId(id)) {
      throw new WodLockedException("error.session.locked", id);
    }
  }

  /**
   * Sets the policy and {@code contentVisibleFrom} of a gym session (personal sessions have none).
   *
   * <ul>
   *   <li>ADVANCE: the chosen local instant, before the session's first day (default: the day
   *       before at 00:00);
   *   <li>DAY_OF: the chosen local instant, on a day of the session (default: its first day at
   *       00:00);
   *   <li>AFTER / AFTER_PARTICIPANTS: the day after its last day at 00:00;
   *   <li>PARTICIPANTS_ONLY: never for the whole gym.
   * </ul>
   */
  private void applyVisibility(Session session, VisibilityPolicy requested, LocalDateTime at) {
    if (session.getGymId() == null) {
      session.setVisibilityPolicy(null);
      session.setContentVisibleFrom(null);
      return;
    }
    VisibilityPolicy policy =
        requested != null ? requested : settingsService.defaultPolicy(session.getGymId());
    settingsService.checkPolicyAllowed(session.getGymId(), policy);
    ZoneId zone = settingsService.zoneOf(session.getGymId());
    LocalDateTime first = session.getDate().atStartOfDay();
    Instant from =
        switch (policy) {
          case ADVANCE -> {
            LocalDateTime local = at != null ? at : first.minusDays(1);
            if (!local.isBefore(first)) {
              throw new InvalidSessionException("error.session.visibility.advance");
            }
            yield local.atZone(zone).toInstant();
          }
          case DAY_OF -> {
            LocalDateTime local = at != null ? at : first;
            if (!session.covers(local.toLocalDate())) {
              throw new InvalidSessionException("error.session.visibility.day.of");
            }
            yield local.atZone(zone).toInstant();
          }
          case AFTER, AFTER_PARTICIPANTS ->
              session.getEndDate().plusDays(1).atStartOfDay(zone).toInstant();
          case PARTICIPANTS_ONLY -> null;
        };
    session.setVisibilityPolicy(policy);
    session.setContentVisibleFrom(from);
  }

  /**
   * Replaces the blocks of the session, in request order, merging existing blocks by ID.
   *
   * @return The IDs of owned WODs no block points at any more (deleted after the flush).
   */
  private List<Long> applyBlocks(
      Session session, List<SessionBlockRequest> requests, AldebaranUserPrincipal principal) {
    Map<Long, SessionBlock> existing =
        session.getBlocks().stream()
            .collect(Collectors.toMap(SessionBlock::getId, Function.identity()));
    Set<Long> keptOwned = new HashSet<>();
    List<SessionBlock> blocks = new ArrayList<>();
    int index = 1;
    for (SessionBlockRequest req : requests) {
      SessionBlock block;
      if (req.id() != null) {
        block = existing.get(req.id());
        if (block == null) {
          throw new ResourceNotFoundException("error.session.block.not.found", req.id());
        }
      } else {
        block = SessionBlock.builder().session(session).build();
      }
      block.setOrderIndex(index++);
      block.setKind(req.kind());
      block.setLabel(req.label());
      block.setThemeEn(req.themeEn());
      block.setThemeFr(req.themeFr());
      block.setOptional(req.optional());
      block.setPlannedMinutes(req.plannedMinutes());
      block.setTextEn(req.kind().isText() ? req.textEn() : null);
      block.setTextFr(req.kind().isText() ? req.textFr() : null);
      Wod wod = resolveWod(session, block.getWod(), req, principal);
      block.setWod(wod);
      if (wod != null && !wod.isStandalone()) {
        keptOwned.add(wod.getId());
      }
      block.setStimulus(
          req.stimulus() != null
              ? req.stimulus()
              : deriveStimulus(req.kind(), wod, session.getDiscipline()));
      blocks.add(block);
    }
    List<Long> orphans =
        existing.values().stream()
            .map(SessionBlock::getWod)
            .filter(w -> w != null && !w.isStandalone() && !keptOwned.contains(w.getId()))
            .map(Wod::getId)
            .distinct()
            .toList();
    session.getBlocks().clear();
    session.getBlocks().addAll(blocks);
    return orphans;
  }

  /**
   * Resolves the WOD of a block: none for text; the inline WOD (updating the block's own WOD in
   * place, or created); a borrowed standalone WOD (copied as the box's competition WOD for a
   * competition block); or the block's own WOD kept as is when the request points at it.
   */
  private Wod resolveWod(
      Session session, Wod current, SessionBlockRequest req, AldebaranUserPrincipal principal) {
    if (req.kind().isText()) {
      return null;
    }
    boolean ownsCurrent = current != null && !current.isStandalone();
    if (req.wod() != null) {
      WodRequest owned = ownedBy(req.wod(), session.getGymId(), req.competition());
      return ownsCurrent
          ? wodService.updateSessionWod(current.getId(), owned)
          : wodService.createSessionWod(owned);
    }
    if (ownsCurrent && Objects.equals(current.getId(), req.wodId())) {
      return current;
    }
    Wod borrowed =
        wodRepository
            .findById(req.wodId())
            .orElseThrow(() -> new ResourceNotFoundException("error.wod.not.found", req.wodId()));
    if (!borrowed.isStandalone()) {
      throw new InvalidSessionException("error.session.wod.not.standalone", req.wodId());
    }
    if (borrowed.isCompetition()) {
      throw new InvalidSessionException("error.session.wod.competition.borrowed", req.wodId());
    }
    if (!wodSecurity.canRead(borrowed.getId(), principal)) {
      throw new AccessDeniedException("WOD " + req.wodId() + " is not readable");
    }
    return req.competition()
        ? wodService.copySessionWod(borrowed.getId(), session.getGymId())
        : borrowed;
  }

  /** The WOD of a block belongs to the session's gym, is never public, competition as asked. */
  private static WodRequest ownedBy(WodRequest wod, Long gymId, boolean competition) {
    return new WodRequest(
        wod.title(),
        wod.wodType(),
        wod.category(),
        wod.scoreType(),
        null,
        gymId,
        false,
        competition,
        wod.descriptionEn(),
        wod.descriptionFr(),
        wod.notesEn(),
        wod.notesFr(),
        wod.tiebreakEn(),
        wod.tiebreakFr(),
        wod.timeCapSeconds(),
        wod.repsPerRound(),
        wod.emomInterval(),
        wod.emomRounds(),
        wod.repScheme(),
        wod.movements());
  }

  /**
   * Default stimulus of a block: ENDURANCE gives THRESHOLD for intervals and AEROBIC otherwise,
   * MOBILITY gives MOBILITY; a SKILL block is POWER in weightlifting, STRENGTH for a strength WOD,
   * SKILL otherwise; ACCESSORY is STRENGTH; WOD and FINISHER are MIXED. Text blocks have none.
   *
   * @param kind The block kind.
   * @param wod The block's WOD, or null.
   * @param discipline The session's discipline.
   * @return The stimulus, or null for a text block.
   */
  static Stimulus deriveStimulus(Kind kind, Wod wod, Discipline discipline) {
    if (kind.isText() || wod == null) {
      return null;
    }
    WodType type = wod.getWodType();
    if (discipline == Discipline.ENDURANCE) {
      return type == WodType.INTERVALS || type == WodType.TABATA
          ? Stimulus.THRESHOLD
          : Stimulus.AEROBIC;
    }
    if (discipline == Discipline.MOBILITY) {
      return Stimulus.MOBILITY;
    }
    return switch (kind) {
      case SKILL -> {
        if (discipline == Discipline.WEIGHTLIFTING) {
          yield Stimulus.POWER;
        }
        yield type == WodType.STRENGTH ? Stimulus.STRENGTH : Stimulus.SKILL;
      }
      case ACCESSORY -> Stimulus.STRENGTH;
      default -> Stimulus.MIXED;
    };
  }

  // -------------------------------------------------------------------------
  // MAPPING
  // -------------------------------------------------------------------------

  private SessionResponse toResponse(Session session, AldebaranUserPrincipal principal) {
    SessionLog myLog =
        sessionLogRepository
            .findByUserIdAndSessionId(principal.getId(), session.getId())
            .orElse(null);
    boolean visible = sessionSecurity.isContentVisible(session, principal, myLog != null);
    boolean locked = sessionLogRepository.existsBySessionId(session.getId());
    SessionCapabilities capabilities =
        new SessionCapabilities(
            sessionSecurity.canModify(session, principal),
            locked,
            visible,
            myLog == null && sessionSecurity.canLogNow(session, principal));
    List<SessionBlockResponse> blocks =
        session.getBlocks().stream().map(b -> toBlockResponse(b, visible)).toList();
    return new SessionResponse(
        session.getId(),
        session.getType(),
        session.getDiscipline(),
        session.getDate(),
        session.getEndDate(),
        session.getTitle(),
        visible ? session.getDescriptionEn() : null,
        visible ? session.getDescriptionFr() : null,
        session.getGymId(),
        session.getAuthorId(),
        session.getVisibilityPolicy(),
        session.getContentVisibleFrom(),
        session.getCreatedAt(),
        session.getUpdatedAt(),
        blocks,
        capabilities,
        myLog != null ? sessionLogService.toResponse(myLog, principal) : null);
  }

  private SessionBlockResponse toBlockResponse(SessionBlock block, boolean visible) {
    Wod wod = block.getWod();
    WodResponse wodResponse =
        visible && wod != null ? wodService.getWodDetail(wod.getId()) : null;
    return new SessionBlockResponse(
        block.getId(),
        block.getOrderIndex(),
        block.getKind(),
        block.isOptional(),
        block.getThemeEn(),
        block.getThemeFr(),
        block.getStimulus(),
        block.getPlannedMinutes(),
        visible ? block.getLabel() : null,
        visible ? block.getTextEn() : null,
        visible ? block.getTextFr() : null,
        wodResponse,
        wod != null && wod.isStandalone(),
        wod != null && wod.isCompetition());
  }

  private SessionSummaryResponse toSummary(
      Session session, boolean logged, AldebaranUserPrincipal principal) {
    List<BlockFrame> frames =
        session.getBlocks().stream()
            .map(b -> new BlockFrame(b.getKind(), b.isOptional(), b.getThemeEn(), b.getThemeFr()))
            .toList();
    return new SessionSummaryResponse(
        session.getId(),
        session.getType(),
        session.getDiscipline(),
        session.getDate(),
        session.getEndDate(),
        session.getTitle(),
        session.getGymId(),
        session.getAuthorId(),
        session.getVisibilityPolicy(),
        session.getContentVisibleFrom(),
        frames,
        sessionSecurity.isContentVisible(session, principal, logged),
        logged);
  }

  private Session findSession(Long id) {
    return sessionRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(ERROR_SESSION_NOT_FOUND, id));
  }

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
