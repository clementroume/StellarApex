package atlas.stellar.vega;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Security configuration for the Spring Boot Admin server (Vega).
 *
 * <p>This configuration is intentionally "open" because security is handled upstream by Traefik via
 * Forward Auth. Requests reaching this service are assumed to be authenticated and authorized
 * (ROLE_ADMIN) by the Antares API.
 */
@Configuration
public class SecurityConfig {

  /**
   * Configures the security filter chain to trust the upstream proxy.
   *
   * @param http the HttpSecurity object
   * @return the configured SecurityFilterChain
   * @throws Exception if an error occurs
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http.authorizeHttpRequests(authorizeRequests -> authorizeRequests.anyRequest().permitAll())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers("/instances", "/instances/**", "/actuator/**"));

    return http.build();
  }
}
