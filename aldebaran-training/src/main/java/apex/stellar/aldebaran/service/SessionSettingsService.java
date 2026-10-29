package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.client.AntaresClient;
import apex.stellar.aldebaran.dto.GymSessionSettingsDto;
import apex.stellar.aldebaran.exception.InvalidSessionException;
import apex.stellar.aldebaran.model.entities.GymSessionSettings;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import apex.stellar.aldebaran.repository.GymSessionSettingsRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session settings of the gyms (D30): default visibility policy and time zone. A gym without
 * settings uses {@code AFTER_PARTICIPANTS} (physical box) or {@code ADVANCE} (virtual program) and
 * {@value #DEFAULT_TIME_ZONE}.
 */
@Service
@RequiredArgsConstructor
public class SessionSettingsService {

  /** Time zone of gyms without settings and of personal sessions. */
  public static final String DEFAULT_TIME_ZONE = "Europe/Paris";

  private final GymSessionSettingsRepository settingsRepository;
  private final AntaresClient antaresClient;

  /**
   * Retrieves the session settings of a gym, defaults included.
   *
   * @param gymId The gym.
   * @return The settings.
   * @throws apex.stellar.aldebaran.exception.AntaresUnavailableException if the gym type is
   *     needed and Antares cannot tell it (503).
   */
  @Transactional(readOnly = true)
  public GymSessionSettingsDto getSettings(Long gymId) {
    return settingsRepository
        .findById(gymId)
        .map(s -> new GymSessionSettingsDto(s.getDefaultVisibility(), s.getTimeZone()))
        .orElseGet(() -> new GymSessionSettingsDto(builtInPolicy(gymId), DEFAULT_TIME_ZONE));
  }

  /**
   * Saves the session settings of a gym.
   *
   * @param gymId The gym.
   * @param request The settings.
   * @return The saved settings.
   * @throws InvalidSessionException for an unknown time zone, or a policy a program cannot use.
   */
  @Transactional
  public GymSessionSettingsDto updateSettings(Long gymId, GymSessionSettingsDto request) {
    try {
      ZoneId.of(request.timeZone());
    } catch (DateTimeException e) {
      throw new InvalidSessionException("error.session.timezone.invalid", request.timeZone());
    }
    checkPolicyAllowed(gymId, request.defaultVisibility());
    settingsRepository.save(
        GymSessionSettings.builder()
            .gymId(gymId)
            .defaultVisibility(request.defaultVisibility())
            .timeZone(request.timeZone())
            .build());
    return request;
  }

  /**
   * Default visibility policy of the gym's new sessions.
   *
   * @param gymId The gym.
   * @return The policy.
   */
  @Transactional(readOnly = true)
  public VisibilityPolicy defaultPolicy(Long gymId) {
    return settingsRepository
        .findById(gymId)
        .map(GymSessionSettings::getDefaultVisibility)
        .orElseGet(() -> builtInPolicy(gymId));
  }

  /**
   * Time zone placing the gym's days in time.
   *
   * @param gymId The gym, or null for a personal session.
   * @return The zone.
   */
  @Transactional(readOnly = true)
  public ZoneId zoneOf(Long gymId) {
    String zone =
        gymId == null
            ? DEFAULT_TIME_ZONE
            : settingsRepository
                .findById(gymId)
                .map(GymSessionSettings::getTimeZone)
                .orElse(DEFAULT_TIME_ZONE);
    return ZoneId.of(zone);
  }

  /**
   * A virtual program only uses {@code ADVANCE} and {@code DAY_OF}.
   *
   * @param gymId The gym.
   * @param policy The policy.
   * @throws InvalidSessionException if the gym is a program and the policy is not allowed.
   */
  public void checkPolicyAllowed(Long gymId, VisibilityPolicy policy) {
    if (!policy.allowedForProgram() && antaresClient.isProgrammingGym(gymId)) {
      throw new InvalidSessionException("error.session.visibility.program", policy);
    }
  }

  private VisibilityPolicy builtInPolicy(Long gymId) {
    return antaresClient.isProgrammingGym(gymId)
        ? VisibilityPolicy.ADVANCE
        : VisibilityPolicy.AFTER_PARTICIPANTS;
  }
}
