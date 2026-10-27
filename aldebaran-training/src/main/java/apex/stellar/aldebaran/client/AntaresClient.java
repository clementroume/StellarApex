package apex.stellar.aldebaran.client;

import apex.stellar.aldebaran.exception.AntaresUnavailableException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client of the Antares internal API ({@code /antares/internal/**}), reached on the Docker network
 * with the shared {@code X-Internal-Secret}, never through Traefik.
 *
 * <p>Aldebaran only knows the caller's memberships (forward-auth headers). This client answers what
 * it cannot know, such as whether another user belongs to a gym. Any failure (timeout, error
 * status, empty answer) raises {@link AntaresUnavailableException}: callers must refuse, never
 * authorize by default.
 */
@Slf4j
@Component
public class AntaresClient {

  private final RestClient restClient;

  /**
   * Builds the client with short timeouts, so that an Antares outage fails fast.
   *
   * @param baseUrl Antares base URL on the internal network.
   * @param internalSecret The secret shared by the StellarApex services.
   * @param timeout Connect and read timeout.
   */
  @Autowired
  public AntaresClient(
      @Value("${application.antares.url}") String baseUrl,
      @Value("${application.security.internal-secret}") String internalSecret,
      @Value("${application.antares.timeout:3s}") Duration timeout) {
    this(RestClient.builder().requestFactory(requestFactory(timeout)), baseUrl, internalSecret);
  }

  /**
   * Builds the client from a prepared builder (tests bind a mock server to it).
   *
   * @param builder The RestClient builder.
   * @param baseUrl Antares base URL.
   * @param internalSecret The shared secret.
   */
  AntaresClient(RestClient.Builder builder, String baseUrl, String internalSecret) {
    this.restClient =
        builder.baseUrl(baseUrl).defaultHeader("X-Internal-Secret", internalSecret).build();
  }

  /**
   * Asks Antares whether a user holds a usable membership in a gym.
   *
   * @param gymId The gym.
   * @param userId The user.
   * @return {@code true} if the user is a usable member of the gym.
   * @throws AntaresUnavailableException if Antares does not give a clear answer.
   */
  public boolean isGymMember(Long gymId, Long userId) {
    try {
      MemberCheck check =
          restClient
              .get()
              .uri("/antares/internal/gyms/{gymId}/members/{userId}", gymId, userId)
              .retrieve()
              .body(MemberCheck.class);
      if (check == null) {
        throw new AntaresUnavailableException("Empty membership answer from Antares", null);
      }
      return check.member();
    } catch (RestClientException e) {
      log.warn("Antares membership check failed (gym {}, user {})", gymId, userId, e);
      throw new AntaresUnavailableException("Antares membership check failed", e);
    }
  }

  /**
   * Asks Antares whether a gym is a virtual programming (no competition, no WOD copies there).
   *
   * @param gymId The gym.
   * @return {@code true} for a virtual programming.
   * @throws AntaresUnavailableException if Antares does not give a clear answer (unknown gym
   *     included).
   */
  public boolean isProgrammingGym(Long gymId) {
    try {
      GymType type =
          restClient
              .get()
              .uri("/antares/internal/gyms/{gymId}", gymId)
              .retrieve()
              .body(GymType.class);
      if (type == null) {
        throw new AntaresUnavailableException("Empty gym type answer from Antares", null);
      }
      return type.programming();
    } catch (RestClientException e) {
      log.warn("Antares gym type check failed (gym {})", gymId, e);
      throw new AntaresUnavailableException("Antares gym type check failed", e);
    }
  }

  private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    return factory;
  }

  /** Answer of {@code GET /antares/internal/gyms/{gymId}/members/{userId}}. */
  record MemberCheck(boolean member) {}

  /** Answer of {@code GET /antares/internal/gyms/{gymId}}. */
  record GymType(boolean programming) {}
}
