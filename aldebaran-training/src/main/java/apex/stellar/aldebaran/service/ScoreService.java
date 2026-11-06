package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.repository.ScoreTeamRepository;
import apex.stellar.aldebaran.model.entities.ScoreTeam;
import apex.stellar.aldebaran.client.AntaresClient;
import apex.stellar.aldebaran.client.AntaresClient.AthleteFacts;
import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.ScoreReferenceData;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.dto.ScoreSetRequest;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.exception.AntaresUnavailableException;
import apex.stellar.aldebaran.exception.InvalidScoreException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.ScoreMapper;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.ScoreSet;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.SessionLog;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.enums.AgeGroup;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.SecurityService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for managing WOD (Workout of the Day) scores. Handles logging, updating,
 * deleting, and calculating personal records and leaderboards.
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

  private static final String ERROR_SCORE_NOT_FOUND = "error.score.not.found";
  private static final String ERROR_WOD_NOT_FOUND = "error.wod.not.found";

  private final ScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final ScoreMapper scoreMapper;
  private final SecurityService securityService;
  private final WodService wodService;
  private final AntaresClient antaresClient;
  private final SessionLogRepository sessionLogRepository;
  private final SessionBlockRepository sessionBlockRepository;
  private final SessionLogService sessionLogService;
  private final ScoreTeamRepository scoreTeamRepository;
  private final MovementRecordService movementRecordService;

  /**
   * Retrieves the current user's scores, optionally only those on a WOD logged in a session (the
   * history of a session block, e.g. Fran borrowed by today's session).
   *
   * @param wodId Optional WOD ID to filter scores.
   * @param sessionId Optional session (needs {@code wodId}): only the scores of its journals.
   * @param pageable Pagination and sorting information.
   * @return A slice of ScoreResponse objects.
   */
  @Transactional(readOnly = true)
  public Slice<ScoreResponse> getMyScores(Long wodId, Long sessionId, Pageable pageable) {
    Long userId = securityService.getCurrentUserId();

    Pageable sortedPageable =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("date").descending());

    Slice<Score> scores;
    if (wodId != null && sessionId != null) {
      scores = scoreRepository.findByUserIdAndWodIdInSession(userId, wodId, sessionId, sortedPageable);
    } else if (wodId != null) {
      scores = scoreRepository.findByUserIdAndWodId(userId, wodId, sortedPageable);
    } else {
      scores = scoreRepository.findByUserId(userId, sortedPageable);
    }

    return scores.map(scoreMapper::toResponse);
  }

  /**
   * Logs a new score for a WOD. A score logged for someone else on a competition WOD (by its
   * judging staff, checked by {@code ScoreSecurity}) is verified at once by whoever logged it.
   *
   * @param request The score details.
   * @return The newly created ScoreResponse.
   * @throws ResourceNotFoundException if the WOD does not exist.
   * @throws InvalidScoreException if the score does not match the WOD's score type.
   */
  @Transactional
  public ScoreResponse logScore(ScoreRequest request) {
    Wod wod =
        wodRepository
            .findById(request.wodId())
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_WOD_NOT_FOUND, request.wodId()));
    Long currentUserId = securityService.getCurrentUserId();
    Long targetUserId = request.userId() != null ? request.userId() : currentUserId;

    Score newScore = scoreMapper.toEntity(request);
    newScore.setWod(wod);
    newScore.setUserId(targetUserId);
    newScore.setGymId(wod.getGymId() != null ? wod.getGymId() : request.gymId());
    newScore.setLoggedAt(LocalDateTime.now());
    newScore.setLoggedBy(currentUserId);
    AthleteFacts athlete = athleteOf(targetUserId, currentUserId);
    newScore.setDivision(athlete.division());
    boolean judged = wod.isCompetition() && !Objects.equals(targetUserId, currentUserId);
    newScore.setVerificationRequested(
        wod.isCompetition() && (judged || request.verificationRequested()));
    if (judged) {
      newScore.setVerifiedBy(currentUserId);
      newScore.setVerifiedAt(LocalDateTime.now());
    }
    attachToSession(
        newScore, wod, request.sessionLogId(), request.sessionBlockId(), targetUserId, currentUserId);
    applySets(newScore, request, wod);
    normalizeAndCheck(newScore, wod);

    List<Long> teammates = teammatesOf(request, wod, targetUserId, newScore.getGymId());
    List<Score> teamScores = new ArrayList<>();
    if (!teammates.isEmpty()) {
      teamScores = formTeam(newScore, wod, teammates, currentUserId);
    } else {
      // Age group of the year of the score (the day is final here: journal day included)
      newScore.setAgeGroup(
          newScore.getDate() == null
              ? null
              : AgeGroup.of(athlete.birthYear(), newScore.getDate().getYear()));
    }

    Score savedScore = scoreRepository.save(newScore);
    scoreRepository.saveAll(teamScores);

    recalculatePrsForUser(wod, targetUserId);
    teammates.forEach(mate -> recalculatePrsForUser(wod, mate));
    movementRecordService.rebuildFor(wod, targetUserId);

    if (savedScore.getDivision() != null && savedScore.getTeam() == null) {
      // Scores logged before the athlete chose a division join it now (after the PR update, which
      // flushes first, so no loaded score writes its old null division back)
      scoreRepository.assignDivisionToUnclassified(targetUserId, savedScore.getDivision());
    }
    if (athlete.birthYear() != null && savedScore.getTeam() == null) {
      // Same for the age group, each older score with the group of its own year
      scoreRepository.assignAgeGroupToUnclassified(targetUserId, athlete.birthYear());
    }

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Division and birth year behind a new score: the logger's own ({@code X-Auth-User-Division},
   * {@code X-Auth-User-Birth-Year}) for a score logged for themselves, else the athlete's, asked to
   * Antares (never the judge's). The birth year only gives the score's age group, never stored.
   *
   * @throws AntaresUnavailableException if Antares cannot tell the athlete's facts (503).
   */
  private AthleteFacts athleteOf(Long targetUserId, Long currentUserId) {
    return Objects.equals(targetUserId, currentUserId)
        ? new AthleteFacts(
            securityService.getCurrentUserDivision(), securityService.getCurrentUserBirthYear())
        : antaresClient.getAthleteFacts(targetUserId);
  }

  /**
   * Updates an existing score. The athlete and the WOD of a score never change, nor does its
   * verification (only staff may update a verified score, see {@code ScoreSecurity}).
   *
   * @param id The ID of the score to update.
   * @param request The updated score details.
   * @return The updated ScoreResponse.
   * @throws InvalidScoreException if the request points at another WOD or does not match the
   *     WOD's score type.
   */
  @Transactional
  public ScoreResponse updateScore(Long id, ScoreRequest request) {
    Score existingScore =
        scoreRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, id));
    Wod wod = existingScore.getWod();
    if (!Objects.equals(wod.getId(), request.wodId())) {
      throw new InvalidScoreException("error.score.wod.mismatch", id, wod.getId());
    }

    Integer previousYear = existingScore.getDate() == null ? null : existingScore.getDate().getYear();
    scoreMapper.updateEntity(request, existingScore);
    existingScore.setGymId(wod.getGymId() != null ? wod.getGymId() : request.gymId());
    if (wod.isCompetition() && !existingScore.isVerified()) {
      existingScore.setVerificationRequested(
          request.verificationRequested()
              || !Objects.equals(existingScore.getLoggedBy(), existingScore.getUserId()));
    }
    if (existingScore.getSessionLogId() != null) {
      // A score stays in its journal: same day and gym as the journal
      sessionLogRepository
          .findById(existingScore.getSessionLogId())
          .ifPresent(
              journal -> {
                existingScore.setDate(journal.getDate());
                if (wod.getGymId() == null) {
                  existingScore.setGymId(journal.getGymId());
                }
              });
    }
    applySets(existingScore, request, wod);
    normalizeAndCheck(existingScore, wod);
    if (existingScore.getTeam() == null
        && existingScore.getDate() != null
        && !Objects.equals(existingScore.getDate().getYear(), previousYear)) {
      // The age group is the one of the score's year
      Integer birthYear =
          athleteOf(existingScore.getUserId(), securityService.getCurrentUserId()).birthYear();
      existingScore.setAgeGroup(AgeGroup.of(birthYear, existingScore.getDate().getYear()));
    }

    Score savedScore = scoreRepository.save(existingScore);

    recalculatePrsForUser(wod, savedScore.getUserId());
    movementRecordService.rebuildFor(wod, savedScore.getUserId());
    if (savedScore.getTeam() != null) {
      // One shared performance: every member's row follows the correction
      for (Score other : scoreRepository.findByTeamId(savedScore.getTeam().getId())) {
        if (!other.getId().equals(savedScore.getId())) {
          copyPerformance(savedScore, other);
          scoreRepository.save(other);
          recalculatePrsForUser(wod, other.getUserId());
        }
      }
    }

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Deletes a score and triggers a PR recalculation for the user.
   *
   * @param scoreId The ID of the score to delete.
   */
  @Transactional
  public void deleteScore(Long scoreId) {
    Score score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Wod wod = score.getWod();
    Long userId = score.getUserId();
    ScoreTeam team = score.getTeam();

    if (team != null && removesWholeTeam(team, userId)) {
      List<Score> rows = scoreRepository.findByTeamId(team.getId());
      scoreRepository.deleteAll(rows);
      scoreTeamRepository.delete(team);
      scoreRepository.flush();
      rows.forEach(row -> recalculatePrsForUser(wod, row.getUserId()));
      return;
    }
    if (team != null) {
      // A member leaves the team: the others keep their score
      team.getMemberIds().remove(userId);
      scoreTeamRepository.save(team);
    }

    scoreRepository.delete(score);
    scoreRepository.flush();

    recalculatePrsForUser(wod, userId);
    movementRecordService.rebuildFor(wod, userId);
  }

  /**
   * Whether deleting a team score removes it for the whole team: yes for the member who logged it,
   * a judge or an admin; a teammate deleting their own row only leaves the team.
   */
  private boolean removesWholeTeam(ScoreTeam team, Long rowUserId) {
    Long currentUserId = securityService.getCurrentUserId();
    return Objects.equals(currentUserId, team.getCreatedBy())
        || !Objects.equals(currentUserId, rowUserId);
  }

  /**
   * Teammates of a team WOD score: distinct, without the athlete, at most the team size (the extra
   * one is the "mirror" of an odd number of athletes), all members of the box where the score was
   * done (Antares confirms each one, 503 if unavailable).
   *
   * @throws InvalidScoreException if the WOD is not a team WOD, the score has no box, the number of
   *     teammates is wrong or one of them is not a member of the box.
   */
  private List<Long> teammatesOf(ScoreRequest request, Wod wod, Long athleteId, Long gymId) {
    if (request.teammateIds() == null || request.teammateIds().isEmpty()) {
      return List.of();
    }
    if (wod.getTeamSize() == null) {
      throw new InvalidScoreException("error.score.team.not.team.wod");
    }
    if (gymId == null) {
      throw new InvalidScoreException("error.score.team.gym.required");
    }
    // Teams train together in a box, never on a program
    if (antaresClient.isProgrammingGym(gymId)) {
      throw new InvalidScoreException("error.score.team.program");
    }
    List<Long> mates =
        request.teammateIds().stream()
            .filter(Objects::nonNull)
            .filter(id -> !id.equals(athleteId))
            .distinct()
            .toList();
    if (mates.isEmpty() || mates.size() > wod.getTeamSize()) {
      throw new InvalidScoreException("error.score.team.size", wod.getTeamSize());
    }
    for (Long mate : mates) {
      if (!antaresClient.isGymMember(gymId, mate)) {
        throw new InvalidScoreException("error.score.team.member", mate);
      }
    }
    return mates;
  }

  /**
   * Creates the team of a new score and one copy of the score per teammate, all with the team's
   * division (men, women, mixed; none while a member has not chosen theirs).
   *
   * @return The teammates' scores, not saved yet.
   */
  private List<Score> formTeam(Score score, Wod wod, List<Long> teammates, Long currentUserId) {
    ScoreTeam team =
        ScoreTeam.builder()
            .wodId(wod.getId())
            .gymId(score.getGymId())
            .createdBy(currentUserId)
            .build();
    team.getMemberIds().add(score.getUserId());
    team.getMemberIds().addAll(teammates);
    team = scoreTeamRepository.save(team);

    List<Division> divisions = new ArrayList<>();
    divisions.add(score.getDivision());
    teammates.forEach(mate -> divisions.add(antaresClient.getUserDivision(mate)));
    Division teamDivision = teamDivision(divisions);

    score.setTeam(team);
    score.setDivision(teamDivision);
    List<Score> copies = new ArrayList<>();
    for (Long mate : teammates) {
      Score copy =
          Score.builder()
              .wod(wod)
              .userId(mate)
              .team(team)
              .division(teamDivision)
              .loggedAt(score.getLoggedAt())
              .loggedBy(score.getLoggedBy())
              .verificationRequested(score.isVerificationRequested())
              .verifiedBy(score.getVerifiedBy())
              .verifiedAt(score.getVerifiedAt())
              .build();
      copyPerformance(score, copy);
      copies.add(copy);
    }
    return copies;
  }

  /** Men or women when every member is, mixed otherwise; null while a member has no division. */
  static @Nullable Division teamDivision(List<Division> divisions) {
    if (divisions.contains(null)) {
      return null;
    }
    if (divisions.stream().allMatch(d -> d == Division.MEN)) {
      return Division.MEN;
    }
    return divisions.stream().allMatch(d -> d == Division.WOMEN) ? Division.WOMEN : Division.MIXED;
  }

  /** Copies the shared performance of a team score (day, box, result, sets) onto another member's. */
  private static void copyPerformance(Score from, Score to) {
    to.setDate(from.getDate());
    to.setGymId(from.getGymId());
    to.setTimeSeconds(from.getTimeSeconds());
    to.setTimeDisplayUnit(from.getTimeDisplayUnit());
    to.setRounds(from.getRounds());
    to.setReps(from.getReps());
    to.setMaxWeightKg(from.getMaxWeightKg());
    to.setTotalLoadKg(from.getTotalLoadKg());
    to.setWeightDisplayUnit(from.getWeightDisplayUnit());
    to.setTotalDistanceMeters(from.getTotalDistanceMeters());
    to.setDistanceDisplayUnit(from.getDistanceDisplayUnit());
    to.setTotalCalories(from.getTotalCalories());
    to.setScaling(from.getScaling());
    to.setTimeCapped(from.getTimeCapped());
    to.setTiebreakSeconds(from.getTiebreakSeconds());
    to.getSets().clear();
    from.getSets()
        .forEach(
            set ->
                to.getSets()
                    .add(new ScoreSet(set.getReps(), set.getWeightKg(), set.isCompleted())));
  }

  /**
   * Verifies a score of a competition WOD (judging), or withdraws its verification.
   *
   * @param scoreId The ID of the score.
   * @param verified {@code true} to verify the score, {@code false} to withdraw the verification.
   * @return The updated ScoreResponse.
   * @throws InvalidScoreException if the WOD of the score is not a competition WOD.
   */
  @Transactional
  public ScoreResponse setVerified(Long scoreId, boolean verified) {
    Score score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));
    if (!score.getWod().isCompetition()) {
      throw new InvalidScoreException("error.score.verify.not.competition");
    }
    if (verified && !score.isVerificationRequested()) {
      throw new InvalidScoreException("error.score.verify.not.requested");
    }

    score.setVerifiedBy(verified ? securityService.getCurrentUserId() : null);
    score.setVerifiedAt(verified ? LocalDateTime.now() : null);

    return scoreMapper.toResponse(scoreRepository.save(score));
  }

  /**
   * Ranks a score among the Personal Records of the other athletes on the same WOD and scaling
   * level, with the ordering of {@link ScoreRanking}.
   *
   * @param scoreId The ID of the score to compare.
   * @return A ScoreComparisonResponse containing rank, total and percentile.
   */
  @Transactional(readOnly = true)
  public ScoreComparisonResponse compareScore(Long scoreId) {
    Score score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Comparator<Score> order = ScoreRanking.rankingFirst(score.getWod().getScoreType());
    // A team counts once (its members share one score), the score's own team excluded
    Long ownTeam = score.getTeam() == null ? null : score.getTeam().getId();
    Map<Long, Score> bestByCompetitor = new HashMap<>();
    scoreRepository
        .findByWodIdAndScalingAndPersonalRecordTrue(score.getWod().getId(), score.getScaling())
        .stream()
        .filter(other -> !Objects.equals(other.getUserId(), score.getUserId()))
        .filter(other -> ownTeam == null || other.getTeam() == null
            || !ownTeam.equals(other.getTeam().getId()))
        .forEach(
            other ->
                bestByCompetitor.merge(
                    other.getTeam() == null ? other.getUserId() : -other.getTeam().getId(),
                    other,
                    (x, y) -> order.compare(x, y) <= 0 ? x : y));
    Collection<Score> others = bestByCompetitor.values();

    long better = others.stream().filter(other -> order.compare(other, score) < 0).count();
    long total = others.size() + 1L;
    long rank = better + 1;
    double percentile = total > 1 ? ((double) (total - rank) / (total - 1)) * 100.0 : 100.0;

    return new ScoreComparisonResponse(rank, total, percentile);
  }

  /**
   * Retrieves the leaderboard of a WOD, optionally restricted to the scores logged in the journals
   * of one session (the session leaderboard of a borrowed WOD such as Fran).
   *
   * @param wodId The WOD.
   * @param scaling The scaling level to rank.
   * @param gymIds The gyms to keep, or null / empty for every gym.
   * @param verifiedOnly Whether to keep verified scores only (competition WODs).
   * @param division The division to keep, or null for all.
   * @param ageGroup The age group to keep, or null for all.
   * @param sessionId The session to keep, or null for every score.
   * @param pageable Pagination information.
   * @return A slice of ScoreResponse objects representing the leaderboard.
   */
  @Transactional(readOnly = true)
  public Slice<ScoreResponse> getLeaderboard(
      Long wodId,
      ScalingLevel scaling,
      Collection<Long> gymIds,
      boolean verifiedOnly,
      @Nullable Division division,
      @Nullable AgeGroup ageGroup,
      @Nullable Long sessionId,
      Pageable pageable) {
    WodResponse wod = wodService.getWodDetail(wodId);

    boolean filterGyms = gymIds != null && !gymIds.isEmpty();
    // A JPQL IN clause needs a non-empty collection, even when the filter is off
    Collection<Long> gyms = filterGyms ? gymIds : Set.of(-1L);

    // A team WOD ranks teams, not athletes: one row per team (in memory)
    if (filterGyms
        || verifiedOnly
        || division != null
        || ageGroup != null
        || sessionId != null
        || wod.teamSize() != null) {
      return filteredLeaderboard(
              wodId,
              wod.scoreType(),
              scaling,
              new Filters(filterGyms, gyms, verifiedOnly, division, ageGroup, sessionId),
              pageable)
          .map(scoreMapper::toResponse);
    }

    Pageable sortedPageable =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            ScoreRanking.leaderboardSort(wod.scoreType()));

    return scoreRepository
        .findLeaderboard(wodId, scaling, sortedPageable)
        .map(scoreMapper::toResponse);
  }

  /**
   * Ranks each athlete by their best score among those kept by the filters, in memory: the ordering
   * lives in {@link ScoreRanking} and cannot be expressed as a "best per athlete" query. The kept
   * scores belong to a few gyms (or to a competition WOD of one gym), so the set stays small.
   */
  private Slice<Score> filteredLeaderboard(
      Long wodId, ScoreType type, ScalingLevel scaling, Filters filters, Pageable pageable) {
    Comparator<Score> best = ScoreRanking.bestFirst(type);
    Map<Long, Score> bestByAthlete = new HashMap<>();
    long session = filters.sessionId() == null ? ScoreRepository.NO_SESSION : filters.sessionId();
    for (Score score :
        scoreRepository.findFilteredByWod(
            wodId,
            filters.filterGyms(),
            filters.gyms(),
            filters.verifiedOnly(),
            filters.division(),
            filters.ageGroup(),
            session)) {
      // A team counts once (negative key, never a user ID); an athlete once per their best score
      Long key = score.getTeam() != null ? -score.getTeam().getId() : score.getUserId();
      bestByAthlete.merge(key, score, (a, b) -> best.compare(a, b) <= 0 ? a : b);
    }

    // Same order as ScoreRanking.leaderboardSort: performance, tiebreak, earliest logged, id
    Comparator<Score> ranking =
        ScoreRanking.rankingFirst(type)
            .thenComparing(Score::getLoggedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Score::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    List<Score> ranked =
        bestByAthlete.values().stream()
            .filter(s -> s.getScaling() == scaling)
            .sorted(ranking)
            .toList();

    int from = (int) Math.min(pageable.getOffset(), ranked.size());
    int to = Math.min(from + pageable.getPageSize(), ranked.size());
    return new SliceImpl<>(ranked.subList(from, to), pageable, to < ranked.size());
  }

  /**
   * Retrieves the scores of a competition WOD submitted for verification and not verified yet
   * (judge queue), oldest first.
   *
   * @param wodId The competition WOD.
   * @return The scores waiting for a judge.
   */
  @Transactional(readOnly = true)
  public List<ScoreResponse> getVerificationQueue(Long wodId) {
    wodService.getWodDetail(wodId); // 404 for an unknown WOD
    return scoreRepository.findVerificationQueue(wodId).stream()
        .map(scoreMapper::toResponse)
        .toList();
  }

  /**
   * Links a score to the athlete's journal of a session. With {@code sessionLogId}, the journal
   * must be the athlete's and its session must contain the WOD. Without it, a score on a session
   * WOD joins the athlete's journal of that session, created when missing ("Séance faite" implied,
   * e.g. a judge logging a competition score). The score takes the journal's day and gym.
   *
   * @throws InvalidScoreException if the journal is another athlete's or its session lacks the WOD.
   */
  private void attachToSession(
      Score score,
      Wod wod,
      Long sessionLogId,
      Long sessionBlockId,
      Long targetUserId,
      Long currentUserId) {
    SessionLog journal;
    score.setSessionBlockId(null);
    if (sessionLogId != null) {
      journal =
          sessionLogRepository
              .findById(sessionLogId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("error.session.log.not.found", sessionLogId));
      if (!Objects.equals(journal.getUserId(), targetUserId)
          || !sessionBlockRepository.existsBySessionIdAndWodId(
              journal.getSession().getId(), wod.getId())) {
        throw new InvalidScoreException("error.score.session.log.mismatch", sessionLogId);
      }
    } else if (!wod.isStandalone()) {
      Session session =
          sessionBlockRepository
              .findSessionOwningWod(wod.getId())
              .orElseThrow(
                  () -> new ResourceNotFoundException("error.session.not.found", wod.getId()));
      journal =
          sessionLogService.findOrCreate(
              session, targetUserId, score.getDate(), session.getGymId(), currentUserId);
    } else {
      return;
    }
    score.setSessionLogId(journal.getId());
    score.setSessionBlockId(
        blockOf(journal.getSession().getId(), wod.getId(), sessionBlockId).getId());
    score.setDate(journal.getDate());
    if (wod.getGymId() == null) {
      score.setGymId(journal.getGymId());
    }
  }

  /**
   * Block a session score is logged for: the requested one (it must hold the WOD), else the first
   * block of the session holding the WOD.
   *
   * @throws InvalidScoreException if the requested block does not hold the WOD in that session.
   */
  private SessionBlock blockOf(Long sessionId, Long wodId, Long requestedBlockId) {
    List<SessionBlock> blocks =
        sessionBlockRepository.findBySessionIdAndWodIdOrderByOrderIndex(sessionId, wodId);
    if (requestedBlockId == null) {
      return blocks.getFirst();
    }
    return blocks.stream()
        .filter(b -> b.getId().equals(requestedBlockId))
        .findFirst()
        .orElseThrow(
            () -> new InvalidScoreException("error.score.session.log.mismatch", requestedBlockId));
  }

  /**
   * Replaces the sets of a strength score (D14) and derives its performance from them: the
   * heaviest complete set for a WEIGHT score (PR and leaderboard unchanged), the sum of reps × load
   * of the complete sets for a LOAD score.
   *
   * @throws InvalidScoreException if the WOD is not scored by load (or NONE), or a load has no
   *     unit.
   */
  private void applySets(Score score, ScoreRequest request, Wod wod) {
    score.getSets().clear();
    if (request.sets() == null || request.sets().isEmpty()) {
      return;
    }
    ScoreType type = wod.getScoreType();
    if (type != ScoreType.WEIGHT && type != ScoreType.LOAD && type != ScoreType.NONE) {
      throw new InvalidScoreException("error.score.sets.type");
    }
    Unit unit = request.weightUnit();
    boolean anyWeight = request.sets().stream().anyMatch(set -> set.weight() != null);
    if (anyWeight && unit == null) {
      throw new InvalidScoreException("error.score.sets.unit.required");
    }
    for (ScoreSetRequest set : request.sets()) {
      score
          .getSets()
          .add(
              ScoreSet.builder()
                  .reps(set.reps())
                  .weightKg(set.weight() != null ? unit.toBase(set.weight()) : null)
                  .completed(set.completed() == null || set.completed())
                  .build());
    }
    if (unit != null) {
      score.setWeightDisplayUnit(unit);
    }
    List<ScoreSet> complete =
        score.getSets().stream()
            .filter(set -> set.isCompleted() && set.getReps() > 0 && set.getWeightKg() != null)
            .toList();
    if (type == ScoreType.WEIGHT && !complete.isEmpty()) {
      score.setMaxWeightKg(complete.stream().mapToDouble(ScoreSet::getWeightKg).max().orElseThrow());
    } else if (type == ScoreType.LOAD && !complete.isEmpty()) {
      score.setTotalLoadKg(
          complete.stream().mapToDouble(set -> set.getReps() * set.getWeightKg()).sum());
    }
  }

  /** Retrieves reference data for WOD Score forms. */
  public ScoreReferenceData getReferenceData() {
    List<String> scalingLevels = Arrays.stream(ScalingLevel.values()).map(Enum::name).toList();
    return new ScoreReferenceData(scalingLevels);
  }

  /**
   * Clears the values that do not apply to the WOD, then checks that the value its score type
   * needs is present. A time-capped TIME score is recorded as the reps completed at the cap. The
   * optional tiebreak is kept only for WODs that define one, and for a TIME WOD only when capped
   * (a finished WOD has no tiebreak).
   *
   * @param score The score about to be saved.
   * @param wod The WOD of the score.
   * @throws InvalidScoreException if the required value is missing or out of range.
   */
  private void normalizeAndCheck(Score score, Wod wod) {
    ScoreType type = wod.getScoreType();
    boolean capped = type == ScoreType.TIME && Boolean.TRUE.equals(score.getTimeCapped());
    score.setTimeCapped(capped);
    if (capped) {
      score.setTimeSeconds(null);
    }
    boolean hasTiebreak = wod.getTiebreakEn() != null || wod.getTiebreakFr() != null;
    if (!hasTiebreak || (type == ScoreType.TIME && !capped)) {
      score.setTiebreakSeconds(null);
    }
    if (type == ScoreType.REPS) {
      toTotalReps(score, wod);
    }
    if (type == ScoreType.ROUNDS
        && score.getRounds() != null
        && wod.getEmomRounds() != null
        && score.getRounds() > wod.getEmomRounds()) {
      throw new InvalidScoreException("error.score.rounds.max", wod.getEmomRounds());
    }

    boolean valid =
        switch (type) {
          case TIME ->
              capped
                  ? score.getReps() != null
                  : score.getTimeSeconds() != null && score.getTimeSeconds() > 0;
          case ROUNDS -> score.getRounds() != null;
          case REPS -> score.getReps() != null;
          case WEIGHT -> score.getMaxWeightKg() != null && score.getMaxWeightKg() > 0;
          case LOAD -> score.getTotalLoadKg() != null;
          case CALORIES -> score.getTotalCalories() != null;
          case DISTANCE -> score.getTotalDistanceMeters() != null;
          case NONE -> true;
        };
    if (!valid) {
      throw new InvalidScoreException(
          capped ? "error.score.capped.reps.required" : "error.score.value.required." + type);
    }
  }

  /**
   * Stores a REPS score as its total reps (canonical, as the Open scores AMRAPs). Rounds + reps
   * entered for a WOD with a fixed round become {@code rounds * repsPerRound + reps}.
   *
   * @param score The score about to be saved.
   * @param wod The WOD of the score.
   * @throws InvalidScoreException if rounds are entered for a WOD without reps per round.
   */
  private void toTotalReps(Score score, Wod wod) {
    if (score.getRounds() == null) {
      return;
    }
    if (wod.getRepsPerRound() == null) {
      throw new InvalidScoreException("error.score.rounds.unknown");
    }
    int extra = score.getReps() != null ? score.getReps() : 0;
    score.setReps(score.getRounds() * wod.getRepsPerRound() + extra);
    score.setRounds(null);
  }

  /**
   * Recalculates which score is the Personal Record (PR) for a user on a specific WOD, with the
   * ordering of {@link ScoreRanking} (scaling tier first, then performance).
   *
   * @param wod The WOD.
   * @param userId The ID of the user.
   */
  private void recalculatePrsForUser(Wod wod, Long userId) {
    if (wod.getScoreType() == ScoreType.NONE) {
      return;
    }

    List<Score> allScores = scoreRepository.findByWodIdAndUserId(wod.getId(), userId);
    if (allScores.isEmpty()) {
      return;
    }

    Score bestScore =
        allScores.stream().min(ScoreRanking.bestFirst(wod.getScoreType())).orElseThrow();
    allScores.forEach(s -> s.setPersonalRecord(s == bestScore));

    // Hibernate only issues UPDATEs for rows that actually changed
    scoreRepository.saveAll(allScores);
  }

  /** Filters of the in-memory leaderboard path. */
  private record Filters(
      boolean filterGyms,
      Collection<Long> gyms,
      boolean verifiedOnly,
      @Nullable Division division,
      @Nullable AgeGroup ageGroup,
      @Nullable Long sessionId) {}
}
