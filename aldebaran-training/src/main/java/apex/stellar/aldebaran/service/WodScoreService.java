package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ScoreComparisonResponse;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.dto.WodScoreResponse;
import apex.stellar.aldebaran.exception.ResourceNotFoundException;
import apex.stellar.aldebaran.mapper.WodScoreMapper;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import apex.stellar.aldebaran.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for logging and managing athlete performance scores.
 *
 * <p>Handles PR (Personal Record) calculations, DTO mapping, and lifecycle management. Security
 * checks are delegated to the Controller/Security Bean layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WodScoreService {

  private static final String ERROR_WOD_NOT_FOUND = "error.wod.not.found";
  private static final String ERROR_SCORE_NOT_FOUND = "error.score.not.found";

  private final WodScoreRepository scoreRepository;
  private final WodRepository wodRepository;
  private final WodScoreMapper scoreMapper;
  private final SecurityService securityService;
  private final WodPrService wodPrService;

  /** Retrieves all scores for the currently authenticated user. */
  @Transactional(readOnly = true)
  public Page<WodScoreResponse> getMyScores(Long wodId, Pageable pageable) {
    Long userId = securityService.getCurrentUserId();

    Pageable sortedPageable =
        pageable.getSort().isSorted()
            ? pageable
            : PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by("date").descending());

    Page<WodScore> scores =
        (wodId != null)
            ? scoreRepository.findByUserIdAndWodId(userId, wodId, sortedPageable)
            : scoreRepository.findByUserId(userId, sortedPageable);

    return scores.map(scoreMapper::toResponse);
  }

  /** Retrieves the leaderboard for a specific WOD. */
  @Transactional(readOnly = true)
  public Page<WodScoreResponse> getLeaderboard(
      Long wodId, ScalingLevel scaling, Pageable pageable) {
    Wod wod =
        wodRepository
            .findById(wodId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_WOD_NOT_FOUND, wodId));

    Sort sort = getSortForScoreType(wod.getScoreType());
    Pageable sortedPageable =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

    return scoreRepository
        .findByWodIdAndScalingAndPersonalRecordTrue(wodId, scaling, sortedPageable)
        .map(scoreMapper::toResponse);
  }

  /**
   * Logs a new score.
   *
   * <p>Supports logging for a third party (if authorized by Security layer). Automatically
   * calculates PR status.
   */
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public WodScoreResponse logScore(WodScoreRequest request) {
    // Determine target User ID: Use request ID if present (Admin/Coach), otherwise current user
    Long targetUserId =
        request.userId() != null ? request.userId() : securityService.getCurrentUserId();

    Wod wod =
        wodRepository
            .findById(request.wodId())
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_WOD_NOT_FOUND, request.wodId()));

    WodScore score = scoreMapper.toEntity(request);
    score.setWod(wod);
    score.setUserId(targetUserId);
    score.setLoggedAt(java.time.LocalDateTime.now());

    // 1. Save first to generate ID (needed for PR comparison)
    score = scoreRepository.save(score);

    // 2. Recalculate PRs for this user/WOD
    // We pass the new score's ID so the helper knows which one is 'current'
    boolean isNewPr = wodPrService.updatePrStatus(wod, targetUserId, score.getId());

    // 3. Update the local object reference.
    // The helper method has already saved the 'true' state to DB if needed.
    score.setPersonalRecord(isNewPr);

    return scoreMapper.toResponse(score);
  }

  /**
   * Updates an existing score.
   *
   * <p>Recalculates PR status as the updated score might become (or stop being) the PR.
   */
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public WodScoreResponse updateScore(Long id, WodScoreRequest request) {
    WodScore score =
        scoreRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, id));

    // Update fields via Mapper
    scoreMapper.updateEntity(request, score);

    // Ensure WOD consistency (in case WOD ID was tampered with, though usually immutable in UI)
    if (!score.getWod().getId().equals(request.wodId())) {
      Wod newWod =
          wodRepository
              .findById(request.wodId())
              .orElseThrow(
                  () -> new ResourceNotFoundException(ERROR_WOD_NOT_FOUND, request.wodId()));
      score.setWod(newWod);
    }

    scoreRepository.save(score);

    // Recalculate PRs
    boolean isPr = wodPrService.updatePrStatus(score.getWod(), score.getUserId(), score.getId());
    score.setPersonalRecord(isPr);

    return scoreMapper.toResponse(score);
  }

  /**
   * Deletes a score.
   *
   * <p>@PreAuthorize handles security checks. If a PR is deleted, the next best score is promoted.
   */
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public void deleteScore(Long scoreId) {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Long userId = score.getUserId();
    Wod wod = score.getWod();

    scoreRepository.delete(score);

    // Recalculate PRs after deletion (pass null as current ID since it's deleted)
    wodPrService.updatePrStatus(wod, userId, null);
  }

  /** Calculates rank and percentile. */
  @Transactional(readOnly = true)
  public ScoreComparisonResponse compareScore(Long scoreId) {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new ResourceNotFoundException(ERROR_SCORE_NOT_FOUND, scoreId));

    Wod wod = score.getWod();
    long total = scoreRepository.countByWodIdAndScaling(wod.getId(), score.getScaling());

    long better =
        switch (wod.getScoreType()) {
          case TIME ->
              scoreRepository.countBetterTime(
                  wod.getId(), score.getScaling(), score.getTimeSeconds());
          case ROUNDS_REPS ->
              scoreRepository.countBetterRoundsReps(
                  wod.getId(), score.getScaling(), score.getRounds(), score.getReps());
          case REPS ->
              scoreRepository.countBetterReps(wod.getId(), score.getScaling(), score.getReps());
          case WEIGHT ->
              scoreRepository.countBetterWeight(
                  wod.getId(), score.getScaling(), score.getMaxWeightKg());
          case LOAD ->
              scoreRepository.countBetterLoad(
                  wod.getId(), score.getScaling(), score.getTotalLoadKg());
          case DISTANCE ->
              scoreRepository.countBetterDistance(
                  wod.getId(), score.getScaling(), score.getTotalDistanceMeters());
          case CALORIES ->
              scoreRepository.countBetterCalories(
                  wod.getId(), score.getScaling(), score.getTotalCalories());
          case NONE -> 0;
        };

    long rank = better + 1;
    double percentile = total > 1 ? ((double) (total - rank) / (total - 1)) * 100.0 : 100.0;

    return new ScoreComparisonResponse(rank, total, percentile);
  }

  private Sort getSortForScoreType(ScoreType type) {
    return switch (type) {
      case TIME -> Sort.by("timeSeconds").ascending();
      case ROUNDS_REPS -> Sort.by("rounds").descending().and(Sort.by("reps").descending());
      case REPS -> Sort.by("reps").descending();
      case WEIGHT -> Sort.by("maxWeightKg").descending();
      case LOAD -> Sort.by("totalLoadKg").descending();
      case DISTANCE -> Sort.by("totalDistanceMeters").descending();
      case CALORIES -> Sort.by("totalCalories").descending();
      case NONE -> Sort.by("date").descending();
    };
  }
}
