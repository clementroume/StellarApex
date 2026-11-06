package apex.stellar.antares.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers access denials raised inside the security filter chain with a localized 403
 * ProblemDetail, written directly on the response.
 *
 * <p>Without it, a rejected CSRF token goes through {@code sendError(403)}; the container's
 * {@code /error} dispatch is then unauthenticated and comes back as a bodiless 401, which Sirius
 * mistakes for an expired session. A CSRF rejection carries {@code code=CSRF_INVALID} so Sirius
 * can refresh the token and retry instead.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

  /** Value of the {@code code} property on a rejected CSRF token. */
  public static final String CSRF_INVALID = "CSRF_INVALID";

  private final MessageSource messageSource;
  private final LocaleResolver localeResolver;
  private final JsonMapper jsonMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    boolean csrf = accessDeniedException instanceof CsrfException;
    String key = csrf ? "error.csrf.invalid" : "error.access.denied";
    String detail = messageSource.getMessage(key, null, localeResolver.resolveLocale(request));
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
    problemDetail.setTitle("Access Denied");
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    if (csrf) {
      problemDetail.setProperty(JwtAuthenticationFilter.PROBLEM_CODE, CSRF_INVALID);
    }

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
