package apex.stellar.aldebaran.config;

import static org.springframework.security.config.Customizer.withDefaults;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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

    // CSRF Configuration:
    // We use a cookie-based repository. Crucially, 'withHttpOnlyFalse' is used, so the Angular
    // frontend can read the CSRF token from the cookie and include it in the 'X-XSRF-TOKEN' header.
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(builder -> builder.secure(true).sameSite("Strict"));

    // The RequestAttributeHandler makes the CSRF token available as a request attribute,
    // which is required for the XorCsrfTokenRequestAttributeHandler default in Spring Security 6.
    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

    http.cors(withDefaults())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(requestHandler)
                    // On ignore CSRF uniquement pour les endpoints techniques (monitoring).
                    .ignoringRequestMatchers("/actuator/**"))
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
        .addFilterBefore(new ForwardedAuthFilter(), UsernamePasswordAuthenticationFilter.class);
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
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private static class ForwardedAuthFilter extends OncePerRequestFilter {
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

      String userId = request.getHeader("X-Auth-User-Id");
      String role = request.getHeader("X-Auth-User-Role");

      if (userId != null && role != null) {
        var auth =
            new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
      }

      chain.doFilter(request, response);
    }
  }
}
