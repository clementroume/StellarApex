package apex.stellar.antares.config;

import apex.stellar.antares.model.User;
import apex.stellar.antares.service.CookieService;
import apex.stellar.antares.service.JwtService;
import apex.stellar.antares.service.SupportSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Filter responsible for authenticating requests based on a JSON Web Token (JWT).
 *
 * <p>This filter intercepts incoming HTTP requests, attempts to extract a JWT from either a
 * configured cookie or the {@code Authorization} header, validates the token using {@link
 * JwtDecoder}, and populates the {@link SecurityContextHolder} if the token is valid.
 *
 * <p><b>Support sessions:</b> when the access token is valid and a {@value
 * SupportSessionService#COOKIE_NAME} cookie is present, the request identity becomes the target
 * user (see {@link SupportSessionService#resolve}). If that session is not usable the request is
 * rejected with a 401 ProblemDetail ({@code code = SUPPORT_SESSION_EXPIRED}) and the cookie is
 * cleared: it never falls back to the admin's own identity. Sensitive operations are refused during
 * a session with a 403 ProblemDetail ({@code code = SUPPORT_SESSION_FORBIDDEN}). {@code
 * /antares/auth/**} (except {@code /verify/api}) and {@code DELETE
 * /antares/admin/support-sessions/current} ignore the support cookie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** ProblemDetail property carrying a machine-readable error code. */
  public static final String PROBLEM_CODE = "code";

  public static final String CODE_SUPPORT_SESSION_EXPIRED = "SUPPORT_SESSION_EXPIRED";
  public static final String CODE_SUPPORT_SESSION_FORBIDDEN = "SUPPORT_SESSION_FORBIDDEN";

  private static final String AUTH_PATH_PREFIX = "/antares/auth/";
  private static final String VERIFY_API_PATH = "/antares/auth/verify/api";
  private static final String END_SUPPORT_SESSION =
      "DELETE /antares/admin/support-sessions/current";

  /** Operations refused while acting as another user ("METHOD path"). */
  private static final Set<String> FORBIDDEN_DURING_SUPPORT =
      Set.of(
          "PUT /antares/users/me/password",
          "DELETE /antares/users/me",
          // Display preferences belong to the screen's owner (the admin), not to the target
          "PATCH /antares/users/me/preferences",
          "POST /antares/admin/support-sessions");

  private final JwtDecoder jwtDecoder;
  private final UserDetailsService userDetailsService;
  private final JwtProperties jwtProperties;
  private final SupportSessionService supportSessionService;
  private final CookieService cookieService;
  private final MessageSource messageSource;
  private final LocaleResolver localeResolver;
  private final JsonMapper jsonMapper;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      User user = authenticateAccessToken(request);

      if (user != null) {
        String supportToken = supportTokenFor(request);

        if (supportToken == null) {
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
          Optional<SupportSessionAuthentication> support =
              supportSessionService.resolve(supportToken, user);
          if (support.isEmpty()) {
            cookieService.clearCookie(SupportSessionService.COOKIE_NAME, response);
            writeProblem(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Support Session Expired",
                "error.support.session.expired",
                CODE_SUPPORT_SESSION_EXPIRED);
            return;
          }
          if (FORBIDDEN_DURING_SUPPORT.contains(operation(request))) {
            log.warn(
                "Support session {}: {} refused",
                support.get().getSession().getId(),
                operation(request));
            writeProblem(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "error.support.session.forbidden",
                CODE_SUPPORT_SESSION_FORBIDDEN);
            return;
          }
          support.get().setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(support.get());
        }
      }
    }

    // Always continue the filter chain (authenticated or not)
    filterChain.doFilter(request, response);
  }

  /**
   * Validates the access token and loads its user.
   *
   * @return the user, or {@code null} when there is no valid access token.
   */
  private User authenticateAccessToken(HttpServletRequest request) {
    String jwt = extractJwtFromRequest(request);
    if (jwt == null) {
      return null;
    }
    try {
      Jwt decodedJwt = jwtDecoder.decode(jwt);
      // A support token is never an access token
      if (JwtService.SUPPORT_TOKEN_TYPE.equals(
          decodedJwt.getClaimAsString(JwtService.CLAIM_TYPE))) {
        return null;
      }
      User user = (User) userDetailsService.loadUserByUsername(decodedJwt.getSubject());
      // Tokens issued before the last password change (reset) are no longer valid
      if (issuedBeforePasswordChange(decodedJwt.getIssuedAt(), user.getPasswordChangedAt())) {
        return null;
      }
      return user;
    } catch (JwtException e) {
      // JWT validation failed - the request will be treated as unauthenticated
      // This is normal for expired tokens or invalid signatures
      log.trace("JWT validation failed: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Whether a token was issued before the last password change. JWT {@code iat} has a one-second
   * precision, so the change time is truncated to the second (a token issued in the same second
   * as the change stays valid).
   */
  static boolean issuedBeforePasswordChange(Instant issuedAt, Instant passwordChangedAt) {
    if (passwordChangedAt == null) {
      return false;
    }
    return issuedAt == null || issuedAt.isBefore(passwordChangedAt.truncatedTo(ChronoUnit.SECONDS));
  }

  /** Returns the support token when present and applicable to this request, else {@code null}. */
  private String supportTokenFor(HttpServletRequest request) {
    String uri = request.getRequestURI();
    boolean ignored =
        (uri.startsWith(AUTH_PATH_PREFIX) && !uri.equals(VERIFY_API_PATH))
            || END_SUPPORT_SESSION.equals(operation(request));
    if (ignored) {
      return null;
    }
    Cookie cookie = WebUtils.getCookie(request, SupportSessionService.COOKIE_NAME);
    return cookie != null && !cookie.getValue().isBlank() ? cookie.getValue() : null;
  }

  /** "METHOD path" of the request, without trailing slash. */
  private static String operation(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri.length() > 1 && uri.endsWith("/")) {
      uri = uri.substring(0, uri.length() - 1);
    }
    return request.getMethod() + " " + uri;
  }

  /** Writes a localized ProblemDetail carrying a {@code code} property. */
  private void writeProblem(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String title,
      String messageKey,
      String code)
      throws IOException {
    String detail =
        messageSource.getMessage(messageKey, null, localeResolver.resolveLocale(request));
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty(PROBLEM_CODE, code);

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    jsonMapper.writeValue(response.getOutputStream(), problemDetail);
  }

  /**
   * Extracts JWT from a cookie. Falls back to the Authorization header if the cookie is not found.
   *
   * @param request The HTTP request
   * @return The JWT token, or null if not found
   */
  private String extractJwtFromRequest(HttpServletRequest request) {
    // 1. Try to get JWT from the cookie
    if (request.getCookies() != null) {
      String jwtFromCookie =
          Arrays.stream(request.getCookies())
              .filter(c -> jwtProperties.accessToken().name().equals(c.getName()))
              .map(Cookie::getValue)
              .findFirst()
              .orElse(null);

      if (jwtFromCookie != null) {
        return jwtFromCookie;
      }
    }

    // 2. Fallback to Authorization header (for API clients)
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }

    return null;
  }
}
