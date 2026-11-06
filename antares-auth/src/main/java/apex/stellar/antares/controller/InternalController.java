package apex.stellar.antares.controller;

import apex.stellar.antares.config.InternalSecretFilter;
import apex.stellar.antares.dto.GymMemberCheckResponse;
import apex.stellar.antares.dto.GymTypeResponse;
import apex.stellar.antares.dto.UserDivisionResponse;
import apex.stellar.antares.service.AthleteProfileService;
import apex.stellar.antares.service.GymService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
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
  private final AthleteProfileService athleteProfileService;

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

  /**
   * A user's competition division and birth year, nothing else. Used by Aldebaran when staff log a
   * score for an athlete: the score takes the athlete's division and age group, never the judge's.
   *
   * @param userId The ID of the user.
   * @return {@code {"division": "MEN"|"WOMEN"|null, "birthYear": 1990|null}}, 404 for an unknown
   *     user.
   */
  @GetMapping("/users/{userId}/division")
  public ResponseEntity<UserDivisionResponse> getDivision(@PathVariable Long userId) {
    return ResponseEntity.ok(athleteProfileService.getScoreProfile(userId));
  }

  /**
   * The IDs of a gym's usable members (see {@code Membership#isUsable}). Used by Aldebaran for the
   * records board of a box, shown to its members only.
   *
   * @param gymId The ID of the gym.
   * @return The user IDs, empty for an unknown gym or a gym without usable members.
   */
  @GetMapping("/gyms/{gymId}/member-ids")
  public ResponseEntity<List<Long>> getMemberIds(@PathVariable Long gymId) {
    return ResponseEntity.ok(gymService.getUsableMemberIds(gymId));
  }
}
