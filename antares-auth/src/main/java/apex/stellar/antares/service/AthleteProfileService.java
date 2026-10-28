package apex.stellar.antares.service;

import apex.stellar.antares.dto.AthleteProfileRequest;
import apex.stellar.antares.dto.BodyWeightRequest;
import apex.stellar.antares.dto.BodyWeightResponse;
import apex.stellar.antares.dto.UserResponse;
import apex.stellar.antares.exception.BusinessRuleException;
import apex.stellar.antares.exception.ResourceNotFoundException;
import apex.stellar.antares.mapper.UserMapper;
import apex.stellar.antares.model.BodyWeight;
import apex.stellar.antares.model.Division;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.BodyWeightRepository;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Athlete profile (D31): division, birth year, height and body weight history, all optional.
 *
 * <p>The division is shared: sent to Aldebaran as {@code X-Auth-User-Division} and copied on each
 * score at logging (a change applies to the next scores, see the root CLAUDE.md). Birth year,
 * height and body weights are private: only their owner reads them, through {@code /users/me}.
 */
@Service
@RequiredArgsConstructor
public class AthleteProfileService {

  /** Maximum number of body weight measures returned (about a year of weekly measures). */
  public static final int HISTORY_SIZE = 100;

  private final UserRepository userRepository;
  private final BodyWeightRepository bodyWeightRepository;
  private final UserMapper userMapper;
  private final Clock clock;

  /**
   * Replaces the athlete profile of the current user (null clears a field).
   *
   * @param currentUser The authenticated user.
   * @param request The new profile.
   * @return The updated user.
   * @throws BusinessRuleException When the birth year is in the future (400).
   */
  @Transactional
  @CacheEvict(value = "users", key = "#currentUser.email")
  public UserResponse updateProfile(User currentUser, AthleteProfileRequest request) {
    if (request.birthYear() != null && request.birthYear() > Year.now(clock).getValue()) {
      throw new BusinessRuleException("validation.birthYear.range");
    }
    User user = reload(currentUser);
    user.setDivision(request.division());
    user.setBirthYear(request.birthYear());
    user.setHeightCm(request.heightCm());
    return userMapper.toUserResponse(userRepository.save(user));
  }

  /**
   * The body weight history of the current user, most recent first.
   *
   * @param currentUser The authenticated user.
   * @return Up to {@value #HISTORY_SIZE} measures.
   */
  @Transactional(readOnly = true)
  public List<BodyWeightResponse> getBodyWeights(User currentUser) {
    return bodyWeightRepository
        .findByUserIdOrderByMeasuredOnDesc(currentUser.getId(), PageRequest.of(0, HISTORY_SIZE))
        .stream()
        .map(AthleteProfileService::toResponse)
        .toList();
  }

  /**
   * Records a body weight measure; a measure of the same day is replaced.
   *
   * @param currentUser The authenticated user.
   * @param request The measure.
   * @return The stored measure.
   */
  @Transactional
  public BodyWeightResponse recordBodyWeight(User currentUser, BodyWeightRequest request) {
    LocalDate day = request.measuredOn() != null ? request.measuredOn() : LocalDate.now(clock);
    BodyWeight measure =
        bodyWeightRepository
            .findByUserIdAndMeasuredOn(currentUser.getId(), day)
            .orElseGet(
                () -> BodyWeight.builder().user(reload(currentUser)).measuredOn(day).build());
    measure.setWeightKg(request.weightKg().setScale(2, RoundingMode.HALF_UP));
    return toResponse(bodyWeightRepository.save(measure));
  }

  /**
   * Deletes one of the current user's measures.
   *
   * @param currentUser The authenticated user.
   * @param id The measure.
   * @throws ResourceNotFoundException When the measure does not exist or belongs to someone else.
   */
  @Transactional
  public void deleteBodyWeight(User currentUser, Long id) {
    BodyWeight measure =
        bodyWeightRepository
            .findByIdAndUserId(id, currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("error.bodyWeight.not.found"));
    bodyWeightRepository.delete(measure);
  }

  /**
   * A user's division, for the internal API (scores logged by staff for an athlete).
   *
   * @param userId The user.
   * @return The division, or null when not chosen.
   * @throws ResourceNotFoundException When the user does not exist.
   */
  @Transactional(readOnly = true)
  public Division getDivision(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("error.user.not.found", userId))
        .getDivision();
  }

  private User reload(User currentUser) {
    return userRepository
        .findById(currentUser.getId())
        .orElseThrow(
            () -> new ResourceNotFoundException("error.user.not.found", currentUser.getId()));
  }

  private static BodyWeightResponse toResponse(BodyWeight measure) {
    return new BodyWeightResponse(measure.getId(), measure.getMeasuredOn(), measure.getWeightKg());
  }
}
