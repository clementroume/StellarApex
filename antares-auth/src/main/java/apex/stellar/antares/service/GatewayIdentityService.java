package apex.stellar.antares.service;

import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the identity forwarded by Traefik to downstream APIs (Aldebaran) after a successful
 * forward-auth check on {@code /antares/auth/verify/api}.
 *
 * <p><b>Header contract</b> (every {@code X-Auth-*} header is listed in Traefik's {@code
 * authResponseHeaders}, so client-forged values are always stripped):
 *
 * <ul>
 *   <li>{@code X-Auth-User-Id}, {@code X-Auth-User-Locale}, {@code X-Internal-Secret}: always.
 *   <li>{@code X-Auth-User-Role}: the platform role ({@code ADMIN|USER}), never a gym role.
 *   <li>{@code X-Auth-Memberships}: every usable membership (see {@link Membership#isUsable()}),
 *       always sent, formatted {@code gymId:ROLE:PERM+PERM} and joined by {@code ,} (e.g. {@code
 *       12:COACH:WOD_WRITE+SCORE_VERIFY,15:ATHLETE:}); empty when there are none. Permissions are
 *       the effective ones (see {@link Membership#effectivePermissions()}).
 *   <li>{@code X-Auth-Actor-Id}: the platform admin's id, only during a support session. The other
 *       headers then describe the supported (target) user, resolved upstream by the JWT filter.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GatewayIdentityService {

  public static final String HEADER_AUTH_USER_ID = "X-Auth-User-Id";
  public static final String HEADER_AUTH_USER_ROLE = "X-Auth-User-Role";
  public static final String HEADER_AUTH_USER_LOCALE = "X-Auth-User-Locale";
  public static final String HEADER_AUTH_MEMBERSHIPS = "X-Auth-Memberships";
  public static final String HEADER_AUTH_ACTOR_ID = "X-Auth-Actor-Id";
  public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";

  private final UserRepository userRepository;
  private final String internalSecret;

  /**
   * Creates the service.
   *
   * @param userRepository used to reload the user with its memberships.
   * @param internalSecret shared secret proving to downstream services that Traefik called Antares.
   */
  public GatewayIdentityService(
      UserRepository userRepository,
      @Value("${application.security.internal-secret}") String internalSecret) {
    this.userRepository = userRepository;
    this.internalSecret = internalSecret;
  }

  /**
   * Builds the forward-auth response for an authenticated identity.
   *
   * @param principal the request identity (the target user during a support session).
   * @param actorId the admin acting through a support session, or {@code null} otherwise.
   * @return 200 with identity headers.
   */
  public ResponseEntity<Void> verify(User principal, @Nullable Long actorId) {
    User user = userRepository.findById(principal.getId()).orElse(principal);

    String memberships =
        user.getMemberships().stream()
            .filter(Membership::isUsable)
            .sorted(Comparator.comparing(Membership::getGymId))
            .map(m -> formatMembership(m.getGymId(), m.getGymRole(), m.effectivePermissions()))
            .collect(Collectors.joining(","));

    ResponseEntity.BodyBuilder response =
        ResponseEntity.ok()
            .header(HEADER_AUTH_USER_ID, String.valueOf(user.getId()))
            .header(HEADER_AUTH_USER_ROLE, user.getPlatformRole().name())
            .header(HEADER_AUTH_USER_LOCALE, user.getLocale())
            .header(HEADER_AUTH_MEMBERSHIPS, memberships)
            .header(HEADER_INTERNAL_SECRET, internalSecret);
    if (actorId != null) {
      response.header(HEADER_AUTH_ACTOR_ID, String.valueOf(actorId));
    }
    return response.build();
  }

  /** Formats one membership as {@code gymId:ROLE:PERM+PERM}. */
  private String formatMembership(Long gymId, GymRole role, Collection<Permission> permissions) {
    String perms = permissions.stream().map(Permission::name).collect(Collectors.joining("+"));
    return gymId + ":" + role.name() + ":" + perms;
  }
}
