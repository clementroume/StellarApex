package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.config.SecurityUtils;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for WodScore operations with security enforcement.
 *
 * <p>Ensures that users can only create/modify/delete their own scores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WodScoreService {

  private final WodScoreRepository scoreRepository;
  private final MessageSource messageSource;

  private String getMessage(String key, Object... args) {
    return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
  }

  /**
   * Saves a WodScore with ownership validation.
   *
   * <p>For new scores: Sets userId to authenticated user. For updates: Validates that the user owns
   * the score.
   *
   * @param score The score to save
   * @return The saved score
   * @throws AccessDeniedException if user tries to modify another user's score
   */
  @Transactional
  public WodScore saveScore(WodScore score) throws AccessDeniedException {
    String authenticatedUserId = SecurityUtils.getCurrentUserId();

    if (score.getId() == null) {
      // New score: enforce authenticated userId
      score.setUserId(authenticatedUserId);
      log.info(
          "Creating new score for user {} on WOD {}", authenticatedUserId, score.getWod().getId());
    } else {
      // Update: verify ownership
      WodScore existing =
          scoreRepository
              .findById(score.getId())
              .orElseThrow(
                  () ->
                      new EntityNotFoundException(
                          getMessage("wod.score.not.found", score.getId())));

      if (!existing.getUserId().equals(authenticatedUserId)) {
        log.warn(
            "User {} attempted to modify score {} owned by user {}",
            authenticatedUserId,
            score.getId(),
            existing.getUserId());
        throw new AccessDeniedException(getMessage("error.score.unauthorized.modify"));
      }
    }

    return scoreRepository.save(score);
  }

  /**
   * Deletes a score with ownership validation.
   *
   * @param scoreId The ID of the score to delete
   * @throws AccessDeniedException if user doesn't own the score
   * @throws EntityNotFoundException if score doesn't exist
   */
  @Transactional
  public void deleteScore(Long scoreId) throws AccessDeniedException {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(
                () -> new EntityNotFoundException(getMessage("wod.score.not.found", scoreId)));

    String authenticatedUserId = SecurityUtils.getCurrentUserId();

    if (!score.getUserId().equals(authenticatedUserId)) {
      log.warn(
          "User {} attempted to delete score {} owned by user {}",
          authenticatedUserId,
          scoreId,
          score.getUserId());
      throw new AccessDeniedException(getMessage("error.score.unauthorized.delete"));
    }

    scoreRepository.delete(score);
    log.info("Deleted score {} for user {}", scoreId, authenticatedUserId);
  }

  /**
   * Retrieves all scores for the authenticated user.
   *
   * @return List of user's scores ordered by date (desc)
   */
  @Transactional(readOnly = true)
  public List<WodScore> getCurrentUserScores() {
    String userId = SecurityUtils.getCurrentUserId();
    return scoreRepository.findByUserIdOrderByDateDesc(userId);
  }

  /**
   * Retrieves a single score with ownership validation.
   *
   * @param scoreId The score ID
   * @return The score if owned by current user
   * @throws AccessDeniedException if user doesn't own the score
   */
  @Transactional(readOnly = true)
  public WodScore getScore(Long scoreId) throws AccessDeniedException {
    WodScore score =
        scoreRepository
            .findById(scoreId)
            .orElseThrow(() -> new EntityNotFoundException("Score introuvable: " + scoreId));

    String authenticatedUserId = SecurityUtils.getCurrentUserId();

    if (!score.getUserId().equals(authenticatedUserId)) {
      throw new AccessDeniedException(getMessage("error.score.unauthorized.access"));
    }

    return score;
  }
}
