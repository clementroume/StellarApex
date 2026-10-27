package apex.stellar.antares.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the internal API ({@code /antares/internal/**}): only callers presenting the shared
 * {@code X-Internal-Secret} get through. Anyone else receives a bare 404, so the API does not
 * reveal that it exists. The secret is compared in constant time.
 *
 * <p>Not a Spring bean on purpose: it is only added to the internal filter chain, never registered
 * as a global servlet filter.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

  /** Header carrying the secret shared by the StellarApex services. */
  public static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";

  private final byte[] expectedSecret;

  /**
   * Creates the filter.
   *
   * @param internalSecret The shared secret; a blank secret rejects every request.
   */
  public InternalSecretFilter(String internalSecret) {
    this.expectedSecret =
        internalSecret == null ? new byte[0] : internalSecret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String provided = request.getHeader(HEADER_INTERNAL_SECRET);
    if (expectedSecret.length == 0
        || provided == null
        || !MessageDigest.isEqual(expectedSecret, provided.getBytes(StandardCharsets.UTF_8))) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
