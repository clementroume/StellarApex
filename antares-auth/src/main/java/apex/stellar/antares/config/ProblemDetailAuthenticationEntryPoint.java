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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers requests to protected endpoints without a valid access token (missing, expired or
 * invalid) with a localized 401 ProblemDetail.
 *
 * <p>Sirius refreshes its tokens only on a 401, so this must not be a 403 (Spring Security's
 * default when no entry point is configured). Authenticated requests lacking rights still get a 403
 * from {@code GlobalExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final MessageSource messageSource;
  private final LocaleResolver localeResolver;
  private final JsonMapper jsonMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    String detail =
        messageSource.getMessage(
            "error.authentication.required", null, localeResolver.resolveLocale(request));
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
    problemDetail.setTitle("Unauthorized");
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
