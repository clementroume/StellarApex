package apex.stellar.aldebaran.config;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.util.StringUtils.hasText;

import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.GymAccess;
import apex.stellar.aldebaran.security.SupportSessionAuditFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Configuration class for Spring Security. Configures CORS, CSRF, session management, and custom
 * authentication filters to handle internal requests forwarded from the API Gateway.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Value("${application.security.internal-secret}")
  private String internalSecret;

  /**
   * Configures the security filter chain.
   *
   * @param http the {@link HttpSecurity} to modify
   * @return the built {@link SecurityFilterChain}
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {

    ForwardedAuthFilter forwardedAuthFilter = new ForwardedAuthFilter(internalSecret);

    http.cors(withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth
                    // Allow unauthenticated access to documentation and actuator endpoints
                    .requestMatchers("/aldebaran-docs", "/aldebaran-docs/**", "/actuator/**")
                    .permitAll()
                    // All other endpoints require authentication
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(forwardedAuthFilter, UsernamePasswordAuthenticationFilter.class)
        // Support session: audit writes once the principal (and its actor) is known
        .addFilterAfter(new SupportSessionAuditFilter(), ForwardedAuthFilter.class);
    return http.build();
  }

  /**
   * Configures CORS settings for the application.
   *
   * @return a {@link CorsConfigurationSource} with the defined settings
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Internal-Secret",
            "X-Auth-User-Id",
            "X-Auth-User-Role",
            "X-Auth-Memberships",
            "X-Auth-User-Division",
            "X-Auth-Actor-Id"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * Filter responsible for validating the internal secret and extracting user identity from headers
   * provided by the API Gateway.
   *
   * <p>Header contract (emitted by Antares via Traefik forward-auth):
   *
   * <ul>
   *   <li>{@code X-Auth-User-Id}: user ID (required);
   *   <li>{@code X-Auth-User-Role}: platform role, ADMIN or USER (required);
   *   <li>{@code X-Auth-Memberships}: usable memberships with effective permissions, as {@code
   *       gymId:ROLE:PERM+PERM} entries joined by {@code ,} (e.g. {@code
   *       12:COACH:WOD_WRITE+SCORE_VERIFY,15:ATHLETE:});
   *   <li>{@code X-Auth-User-Division}: {@code MEN} or {@code WOMEN}, empty when not chosen
   *       (optional; unknown values are ignored);
   *   <li>{@code X-Auth-Actor-Id}: the platform admin acting as this user through an audited support
   *       session (optional). The other headers then describe the target user, whose rights apply.
   * </ul>
   *
   * <p>Membership parsing is tolerant: a malformed entry is skipped (and logged), the other entries
   * are kept.
   */
  @Slf4j
  static class ForwardedAuthFilter extends OncePerRequestFilter {

    static final String HEADER_USER_ID = "X-Auth-User-Id";
    static final String HEADER_USER_ROLE = "X-Auth-User-Role";
    static final String HEADER_MEMBERSHIPS = "X-Auth-Memberships";
    static final String HEADER_ACTOR_ID = "X-Auth-Actor-Id";
    static final String HEADER_DIVISION = "X-Auth-User-Division";

    // Compared in constant time; an empty secret rejects every request
    private final byte[] internalSecret;

    ForwardedAuthFilter(String internalSecret) {
      this.internalSecret =
          internalSecret == null ? new byte[0] : internalSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses the {@code X-Auth-Memberships} header.
     *
     * @param header The raw header value (may be null or blank).
     * @return The memberships keyed by gym ID; malformed entries are skipped.
     */
    static Map<Long, GymAccess> parseMemberships(String header) {
      if (!hasText(header)) {
        return Map.of();
      }
      Map<Long, GymAccess> memberships = new HashMap<>();
      for (String entry : header.split(",")) {
        if (entry.isBlank()) {
          continue;
        }
        String[] parts = entry.split(":", 3);
        try {
          Long gymId = Long.parseLong(parts[0].trim());
          String role = parts.length > 1 ? parts[1].trim() : "";
          if (role.isEmpty()) {
            throw new IllegalArgumentException("missing role");
          }
          Set<String> permissions =
              parts.length > 2
                  ? Arrays.stream(parts[2].split("\\+"))
                      .map(String::trim)
                      .filter(p -> !p.isEmpty())
                      .collect(Collectors.toSet())
                  : Set.of();
          memberships.put(gymId, new GymAccess(role, permissions));
        } catch (IllegalArgumentException e) {
          log.warn("Skipping malformed membership entry '{}': {}", entry, e.getMessage());
        }
      }
      return Map.copyOf(memberships);
    }

    /**
     * Processes the incoming request to validate the internal secret and establish authentication.
     *
     * @param request the {@link HttpServletRequest}
     * @param response the {@link HttpServletResponse}
     * @param chain the {@link FilterChain}
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain)
        throws ServletException, IOException {

      String headerSecret = request.getHeader("X-Internal-Secret");

      if (internalSecret.length == 0
          || headerSecret == null
          || !MessageDigest.isEqual(internalSecret, headerSecret.getBytes(StandardCharsets.UTF_8))) {
        response.sendError(
            HttpServletResponse.SC_FORBIDDEN, "Access Denied: Invalid Internal Secret");
        return;
      }

      String userIdStr = request.getHeader(HEADER_USER_ID);
      String platformRole = request.getHeader(HEADER_USER_ROLE);

      if (userIdStr != null && hasText(platformRole)) {
        try {
          Long userId = Long.parseLong(userIdStr.trim());
          String actorIdStr = request.getHeader(HEADER_ACTOR_ID);
          Long actorId = hasText(actorIdStr) ? Long.parseLong(actorIdStr.trim()) : null;

          AldebaranUserPrincipal principal =
              AldebaranUserPrincipal.builder()
                  .id(userId)
                  .platformRole(platformRole.trim())
                  .memberships(parseMemberships(request.getHeader(HEADER_MEMBERSHIPS)))
                  .division(Division.parse(request.getHeader(HEADER_DIVISION)))
                  .actorId(actorId)
                  .build();

          var auth =
              new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
          SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (NumberFormatException e) {
          log.warn(
              "Failed to parse identity headers. userId='{}', actorId='{}'. Error: {}",
              userIdStr,
              request.getHeader(HEADER_ACTOR_ID),
              e.getMessage());
        }
      }
      chain.doFilter(request, response);
    }

    /**
     * Determines whether the filter should be skipped for the given request.
     *
     * @param request the current HTTP request
     * @return {@code true} if the filter should not be applied, {@code false} otherwise
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
      String path = request.getServletPath();
      return path.startsWith("/actuator") || path.startsWith("/aldebaran-docs");
    }
  }
}
