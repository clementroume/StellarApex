package com.antares.api.config;

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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Main web security configuration for the application.
 *
 * <p>This setup is designed for a stateless API using HttpOnly JWT cookies and "Double Submit
 * Cookie" CSRF protection.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables method-level security (e.g., @PreAuthorize)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  /**
   * Configures the primary security filter chain that applies to all HTTP requests.
   *
   * @param http The HttpSecurity object to configure.
   * @return The configured SecurityFilterChain.
   * @throws Exception if an error occurs during configuration.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    // Handler to ensure CSRF token is accessible by JavaScript (for the header)
    // but not sent as a request attribute.
    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
    requestHandler.setCsrfRequestAttributeName(null);

    http.cors(withDefaults()) // Enable CORS using the corsConfigurationSource bean
        .csrf(
            csrf ->
                csrf
                    // Use CookieCsrfTokenRepository, setting HttpOnly=false so Angular can read it.
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler)
                    // Disable CSRF protection for public auth endpoints and actuators
                    .ignoringRequestMatchers("/api/v1/auth/**", "/actuator/**"))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Secure Swagger/OpenAPI endpoints (admin only)
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                    // Public endpoints
                    .requestMatchers("/api/v1/auth/**", "/actuator/**")
                    .permitAll()
                    // All other endpoints require authentication
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session ->
                // Configure the application to be stateless (no HttpSession)
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Add the custom JWT filter before the standard username/password filter
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * Defines the Cross-Origin Resource Sharing (CORS) configuration.
   *
   * <p>This bean allows the Angular frontend (running on '<a
   * href="https://antares.local">https://antares.local</a>') to make requests to this API.
   *
   * @return The CorsConfigurationSource.
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {

    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(this.allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(true); // Crucial for sending/receiving cookies

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}
