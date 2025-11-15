package atlas.stellar.antares.config;

import atlas.stellar.antares.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A custom Spring Security filter that intercepts all HTTP requests to authenticate users based on
 * a JWT token stored in an HttpOnly cookie.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserDetailsService userDetailsService;

  /**
   * Reads the JWT access token from cookies, validates it, and sets the user authentication in the
   * Spring Security context.
   *
   * @param request The incoming HTTP request.
   * @param response The outgoing HTTP response.
   * @param filterChain The filter chain to pass the request to the next filter.
   * @throws ServletException If a servlet error occurs.
   * @throws IOException If an I/O error occurs.
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    final String accessToken =
        jwtService.getJwtFromCookies(request, jwtService.getAccessTokenCookieName());

    if (accessToken == null) {
      filterChain.doFilter(request, response);
      return;
    }

    final String userEmail = jwtService.extractUsername(accessToken);
    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
      if (jwtService.isTokenValid(accessToken, userDetails)) {
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
      }
    }

    filterChain.doFilter(request, response);
  }
}
