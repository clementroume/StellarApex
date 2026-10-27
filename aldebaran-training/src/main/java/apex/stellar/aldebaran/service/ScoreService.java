package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.ScoreReferenceData;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.exception.InvalidScoreException;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.ScoreMapper;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.security.SecurityService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

  /**
   * Retrieves a paginated list of scores for the currently authenticated user.
   *
   * @param wodId Optional WOD ID to filter scores.
   * @param pageable Pagination and sorting information.
   * @return A slice of ScoreResponse objects.
   */
  @Transactional(readOnly = true)
  public Slice<ScoreResponse> getMyScores(Long wodId, Pageable pageable) {
    Long userId = securityService.getCurrentUserId();

    Pageable sortedPageable =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("date").descending());

    Slice<Score> scores =
        (wodId != null)
            ? scoreRepository.findByUserIdAndWodId(userId, wodId, sortedPageable)
            : scoreRepository.findByUserId(userId, sortedPageable);

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
    if (wod.isCompetition() && !Objects.equals(targetUserId, currentUserId)) {
      newScore.setVerifiedBy(currentUserId);
      newScore.setVerifiedAt(LocalDateTime.now());
    }
    normalizeAndCheck(newScore, wod);

    Score savedScore = scoreRepository.save(newScore);

    recalculatePrsForUser(wod, targetUserId);

    return scoreMapper.toResponse(savedScore);
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
   * Retrieves the leaderboard for a specific WOD and scaling level: the Personal Record of each
   * athlete whose best score is at that level, best first, optionally limited to the scores
   * performed in one gym or in any of the current user's gyms, and to verified scores.
   *
   * @param wodId The ID of the WOD.
   * @param scaling The scaling level (e.g., RX, Scaled).
   * @param gymId The gym to keep, or null.
   * @param myGyms Whether to keep the scores performed in the current user's gyms (ignored when
   *     {@code gymId} is set); none when the user has no gym.
   * @param verifiedOnly Whether to keep verified scores only (competition WODs).
   * @param pageable Pagination information.
   * @return A slice of ScoreResponse objects representing the leaderboard.
   */
  @Transactional(readOnly = true)
  public Slice<ScoreResponse> getLeaderboard(
      Long wodId,
      ScalingLevel scaling,
      Long gymId,
      boolean myGyms,
      boolean verifiedOnly,
      Pageable pageable) {
    WodResponse wod = wodService.getWodDetail(wodId);

    Pageable sortedPageable =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            ScoreRanking.leaderboardSort(wod.scoreType()));

    // getCurrentUserMemberGymIds is {-1} without any gym: the IN clause then keeps nothing
    Set<Long> gymIds =
        gymId != null
            ? Set.of(gymId)
            : myGyms ? securityService.getCurrentUserMemberGymIds() : Set.of(-1L);
    boolean filterGyms = gymId != null || myGyms;

    return scoreRepository
        .findLeaderboard(wodId, scaling, filterGyms, gymIds, verifiedOnly, sortedPageable)
        .map(scoreMapper::toResponse);
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
}
