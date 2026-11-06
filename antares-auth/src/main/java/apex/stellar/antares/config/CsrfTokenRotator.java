package apex.stellar.antares.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Re-issues the {@code XSRF-TOKEN} cookie when the identity behind the browser changes (sign-in,
 * sign-out, start or end of an admin support session).
 *
 * <p>The token is otherwise stable for the whole session ({@link SecurityConfig}): rotating it on
 * ordinary requests made a GET invalidate the header of a POST in flight. Never call this on a
 * token refresh or an ordinary request.
 */
@Component
@RequiredArgsConstructor
public class CsrfTokenRotator {

  private final CsrfTokenRepository csrfTokenRepository;

  /**
   * Writes a new token cookie on the response of the current request.
   *
   * @param response the response of the request being handled.
   */
  public void rotate(HttpServletResponse response) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      HttpServletRequest request = attributes.getRequest();
      csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
    }
  }
}
