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
 * Security configuration for Aldebaran Training API. Uses JWT from Antares Auth for authentication.
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
   * Configures the security filter chain for the application, including CSRF protection, CORS
   * configuration, authentication, and session management. This method returns a fully built {@link
   * SecurityFilterChain} tailored to the application's security needs.
   *
   * @param http the {@link HttpSecurity} configuration object that allows customization of various
   *     security aspects, such as authentication, session policies, and request authorization.
   * @return a {@link SecurityFilterChain} instance configured with the defined security policies.
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
   * Creates and configures a {@link CorsConfigurationSource} bean to define Cross-Origin Resource
   * Sharing (CORS) policies for the application. This includes setting allowed origins, HTTP
   * methods, headers, and credentials for incoming requests matching the specified paths.
   *
   * <p>The CORS configuration is registered for all paths ("/**") to ensure compliance with
   * security requirements while enabling cross-origin requests from authorized sources.
   *
   * @return an instance of {@link CorsConfigurationSource} containing the specified CORS policies.
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

  @Slf4j
  private static class ForwardedAuthFilter extends OncePerRequestFilter {

    private final String internalSecret;

    public ForwardedAuthFilter(String internalSecret) {
      this.internalSecret = internalSecret;
    }

    /**
     * Filters incoming requests to extract authentication details from custom headers.
     *
     * @param request The HTTP request.
     * @param response The HTTP response.
     * @param chain The filter chain.
     * @throws ServletException If a servlet error occurs.
     * @throws IOException If an I/O error occurs.
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
  }
}
