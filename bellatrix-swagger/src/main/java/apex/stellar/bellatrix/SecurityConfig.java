package apex.stellar.bellatrix;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * "Zero-Trust Internal" Security Configuration.
 *
 * <p>IMPORTANT: This service is configured in "Permit All" mode because security is delegated to
 * the infrastructure (Security Offloading).
 *
 * <ol>
 *   <li>Authentication is handled upstream by the Proxy (Traefik) via the Forward Auth mechanism.
 *   <li>Only validated requests (ADMIN role) reach this service.
 *   <li>This service is not exposed publicly outside the Docker network.
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
        // Disable CSRF: unnecessary for a read-only stateless documentation API
        .csrf(AbstractHttpConfigurer::disable)
        // Enable CORS: required for the Swagger UI JavaScript to load JSON resources
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // Authorization: Trust the proxy; all incoming traffic is deemed legitimate
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        // Stateless: No server-side session, saving resources
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    return http.build();
  }

  /** CORS configuration to authorize the main domain and subdomains. */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(List.of(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
