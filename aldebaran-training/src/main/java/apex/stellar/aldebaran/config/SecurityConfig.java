package apex.stellar.aldebaran.config;

import static org.springframework.security.config.Customizer.withDefaults;

import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        .addFilterBefore(
            new ForwardedAuthFilter(internalSecret), UsernamePasswordAuthenticationFilter.class);
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
            "X-Auth-Gym-Id",
            "X-Auth-User-Role",
            "X-Auth-User-Permissions"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * Filter responsible for validating the internal secret and extracting user identity from headers
   * provided by the API Gateway.
   */
  @Slf4j
  private static class ForwardedAuthFilter extends OncePerRequestFilter {

    private final String internalSecret;

    public ForwardedAuthFilter(String internalSecret) {
      this.internalSecret = internalSecret;
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

      if (headerSecret == null || !headerSecret.equals(internalSecret)) {
        response.sendError(
            HttpServletResponse.SC_FORBIDDEN, "Access Denied: Invalid Internal Secret");
        return;
      }

      String userIdStr = request.getHeader("X-Auth-User-Id");
      String gymIdStr = request.getHeader("X-Auth-Gym-Id");
      String role = request.getHeader("X-Auth-User-Role");
      String permissionsStr = request.getHeader("X-Auth-User-Permissions");

      if (userIdStr != null && role != null) {
        try {
          Long userId = Long.parseLong(userIdStr);
          Long gymId = gymIdStr != null ? Long.parseLong(gymIdStr) : null;
          List<String> permissions =
              permissionsStr != null
                  ? Arrays.asList(permissionsStr.split(","))
                  : Collections.emptyList();

          AldebaranUserPrincipal principal =
              AldebaranUserPrincipal.builder()
                  .id(userId)
                  .gymId(gymId)
                  .role(role)
                  .permissions(permissions)
                  .build();

          var auth =
              new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
          SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (NumberFormatException e) {
          log.warn(
              "Failed to parse identity headers. userId='{}', gymId='{}'. Error: {}",
              userIdStr,
              gymIdStr,
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
