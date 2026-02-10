package apex.stellar.antares.config;

import apex.stellar.antares.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter responsible for authenticating requests based on a JSON Web Token (JWT).
 *
 * <p>This filter intercepts incoming HTTP requests, attempts to extract a JWT from either a
 * configured cookie or the {@code Authorization} header, validates the token using {@link
 * JwtDecoder}, and populates the {@link SecurityContextHolder} if the token is valid.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtDecoder jwtDecoder;
  private final UserDetailsService userDetailsService;
  private final JwtProperties jwtProperties;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // Extract JWT from a cookie or Authorization header
      String jwt = extractJwtFromRequest(request);

      if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        // Decode and validate JWT
        Jwt decodedJwt = jwtDecoder.decode(jwt);

        // Load user details
        String email = decodedJwt.getSubject();
        User user = (User) userDetailsService.loadUserByUsername(email);

        // Create an authentication token
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, decodedJwt, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Set authentication in the security context
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    } catch (JwtException e) {
      // JWT validation failed - the request will be treated as unauthenticated
      // This is normal for expired tokens or invalid signatures
      log.trace("JWT validation failed: {}", e.getMessage());
    }

    // Always continue the filter chain (authenticated or not)
    filterChain.doFilter(request, response);
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
