package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.GymSessionSettingsDto;
import apex.stellar.aldebaran.service.SessionSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST Controller of a gym's session settings (default visibility policy, time zone). */
@RestController
@RequestMapping("/aldebaran/gyms/{gymId}/session-settings")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Daily sessions, blocks and visibility")
public class GymSessionSettingsController {

  private final SessionSettingsService settingsService;

  /**
   * Retrieves the session settings of a gym (its members).
   *
   * @param gymId The gym.
   * @return The settings, defaults included.
   */
  @GetMapping
  @PreAuthorize("@sessionSecurity.canReadSettings(#gymId, principal)")
  public ResponseEntity<GymSessionSettingsDto> getSettings(@PathVariable Long gymId) {

    return ResponseEntity.ok(settingsService.getSettings(gymId));
  }

  /**
   * Saves the session settings of a gym (MANAGE_SETTINGS).
   *
   * @param gymId The gym.
   * @param request The settings.
   * @return The saved settings.
   */
  @PutMapping
  @PreAuthorize("@sessionSecurity.canWriteSettings(#gymId, principal)")
  public ResponseEntity<GymSessionSettingsDto> updateSettings(
      @PathVariable Long gymId, @Valid @RequestBody GymSessionSettingsDto request) {

    return ResponseEntity.ok(settingsService.updateSettings(gymId, request));
  }
}
