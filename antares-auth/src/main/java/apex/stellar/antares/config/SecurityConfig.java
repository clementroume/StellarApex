package apex.stellar.antares.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Main security configuration for the Antares API.
 *
 * <p>This configuration establishes a stateless security architecture using JWTs (JSON Web Tokens)
 * stored in HttpOnly cookies, reinforced by Double-Submit Cookie CSRF protection. It integrates
 * with Spring Security's OAuth2 Resource Server to handle token validation and principal
 * extraction.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  /**
   * Configures the primary security filter chain for the application.
   *
   * @param http The {@link HttpSecurity} builder.
   * @return The configured {@link SecurityFilterChain}.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {

    http.cors(withDefaults()) // Delegate to the corsConfigurationSource bean
        .csrf(
            csrf ->
                csrf.spa()
                    .ignoringRequestMatchers(
                        "/antares/auth/register",
                        "/antares/auth/login",
                        "/antares/auth/refresh-token",
                        "/actuator/**"))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth
                    // Allow unauthenticated access to auth endpoints, docs, and actuators
                    .requestMatchers(
                        "/antares/auth/**", "/antares-docs", "/antares-docs/**", "/actuator/**")
                    .permitAll()
                    // Require authentication for all other endpoints
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session ->
                session
                    // Enforce statelessness; no HttpSession will be created or used
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    return http.build();
  }

  /**
   * Defines the Cross-Origin Resource Sharing (CORS) configuration.
   *
   * <p>Allows the Angular frontend to communicate with this API, exposing the necessary headers and
   * allowing credentials (cookies).
   *
   * @return The CORS configuration source.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(true); // Essential for Cookie-based auth

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
