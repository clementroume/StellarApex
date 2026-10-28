package apex.stellar.antares.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP of a request, for per-IP throttles (registration, password reset).
 *
 * <p>Antares publishes no port: public requests always come through Traefik, which drops the {@code
 * X-Forwarded-*} headers sent by untrusted clients (no {@code forwardedHeaders.trustedIPs} on the
 * entrypoints) and appends the peer address as the last {@code X-Forwarded-For} entry. Only that
 * last entry is read, never the first one, which a client could choose; without the header (tests,
 * Docker network) the socket address is used.
 */
@Component
public class ClientIpResolver {

  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  /**
   * Returns the client IP of the request.
   *
   * @param request The HTTP request.
   * @return The last {@code X-Forwarded-For} entry, or the remote address.
   */
  public String resolve(HttpServletRequest request) {
    String forwarded = request.getHeader(X_FORWARDED_FOR);
    if (forwarded != null && !forwarded.isBlank()) {
      String[] hops = forwarded.split(",");
      String last = hops[hops.length - 1].trim();
      if (!last.isEmpty()) {
        return last;
      }
    }
    return request.getRemoteAddr();
  }
}
