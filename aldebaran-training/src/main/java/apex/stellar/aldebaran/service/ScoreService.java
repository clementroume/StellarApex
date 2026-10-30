package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.client.AntaresClient;
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
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.SessionBlockRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.SecurityService;
import java.time.LocalDateTime;
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
    newScore.setDivision(divisionOf(targetUserId, currentUserId));
    boolean judged = wod.isCompetition() && !Objects.equals(targetUserId, currentUserId);
    newScore.setVerificationRequested(
        wod.isCompetition() && (judged || request.verificationRequested()));
    if (judged) {
      newScore.setVerifiedBy(currentUserId);
      newScore.setVerifiedAt(LocalDateTime.now());
    }
    attachToSession(newScore, wod, request.sessionLogId(), targetUserId, currentUserId);
    applySets(newScore, request, wod);
    normalizeAndCheck(newScore, wod);

    Score savedScore = scoreRepository.save(newScore);

    recalculatePrsForUser(wod, targetUserId);

    if (savedScore.getDivision() != null) {
      // Scores logged before the athlete chose a division join it now (after the PR update, which
      // flushes first, so no loaded score writes its old null division back)
      scoreRepository.assignDivisionToUnclassified(targetUserId, savedScore.getDivision());
    }

    return scoreMapper.toResponse(savedScore);
  }

  /**
   * Division copied on a new score: the logger's own ({@code X-Auth-User-Division}) for a score
   * logged for themselves, else the athlete's, asked to Antares (never the judge's division).
   *
   * @throws AntaresUnavailableException if Antares cannot tell the athlete's division (503).
   */
  private @Nullable Division divisionOf(Long targetUserId, Long currentUserId) {
    return Objects.equals(targetUserId, currentUserId)
        ? securityService.getCurrentUserDivision()
        : antaresClient.getUserDivision(targetUserId);
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

    Score savedScore = scoreRepository.save(existingScore);

    recalculatePrsForUser(wod, savedScore.getUserId());

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

    scoreRepository.delete(score);
    scoreRepository.flush();

    recalculatePrsForUser(wod, userId);
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
    List<Score> others =
        scoreRepository
            .findByWodIdAndScalingAndPersonalRecordTrue(score.getWod().getId(), score.getScaling())
            .stream()
            .filter(other -> !Objects.equals(other.getUserId(), score.getUserId()))
            .toList();

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
      @Nullable Long sessionId,
      Pageable pageable) {
    WodResponse wod = wodService.getWodDetail(wodId);

    boolean filterGyms = gymIds != null && !gymIds.isEmpty();
    // A JPQL IN clause needs a non-empty collection, even when the filter is off
    Collection<Long> gyms = filterGyms ? gymIds : Set.of(-1L);

    if (filterGyms || verifiedOnly || division != null || sessionId != null) {
      return filteredLeaderboard(
              wodId,
              wod.scoreType(),
              scaling,
              new Filters(filterGyms, gyms, verifiedOnly, division, sessionId),
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
            session)) {
      bestByAthlete.merge(score.getUserId(), score, (a, b) -> best.compare(a, b) <= 0 ? a : b);
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
      Score score, Wod wod, Long sessionLogId, Long targetUserId, Long currentUserId) {
    SessionLog journal;
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
    score.setDate(journal.getDate());
    if (wod.getGymId() == null) {
      score.setGymId(journal.getGymId());
    }
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
      @Nullable Long sessionId) {}
}
