package apex.stellar.antares.controller;

import apex.stellar.antares.config.InternalSecretFilter;
import apex.stellar.antares.dto.GymMemberCheckResponse;
import apex.stellar.antares.dto.GymTypeResponse;
import apex.stellar.antares.service.GymService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API called by Aldebaran on the Docker network, never through Traefik (the
 * public {@code /antares} route excludes {@code /antares/internal}).
 *
 * <p>Every request must carry the shared {@code X-Internal-Secret}; {@link InternalSecretFilter}
 * answers 404 otherwise. There is no user here: callers enforce their own rules and only ask
 * Antares for facts they cannot know.
 */
@Hidden
@RestController
@RequestMapping("/antares/internal")
@RequiredArgsConstructor
public class InternalController {

  private final GymService gymService;

  /**
   * Tells whether a gym is a virtual programming or a physical box. Used by Aldebaran to keep
   * competitions and WOD copies to physical gyms.
   *
   * @param gymId The ID of the gym.
   * @return {@code {"programming": true|false}}, 404 for an unknown gym.
   */
  @GetMapping("/gyms/{gymId}")
  public ResponseEntity<GymTypeResponse> getGymType(@PathVariable Long gymId) {
    return ResponseEntity.ok(new GymTypeResponse(gymService.isProgramming(gymId)));
  }

  /**
   * Tells whether a user holds a usable membership in a gym (ACTIVE membership, and ACTIVE gym or
   * OWNER/PROGRAMMER role). Used by Aldebaran before gym staff log a score for an athlete.
   *
   * @param gymId The ID of the gym.
   * @param userId The ID of the user.
   * @return {@code {"member": true|false}}.
   */
  @GetMapping("/gyms/{gymId}/members/{userId}")
  public ResponseEntity<GymMemberCheckResponse> isMember(
      @PathVariable Long gymId, @PathVariable Long userId) {
    return ResponseEntity.ok(new GymMemberCheckResponse(gymService.isUsableMember(gymId, userId)));
  }
}
