package apex.stellar.antares.config;

import static org.springframework.security.config.Customizer.withDefaults;

import apex.stellar.antares.model.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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

  private final JwtProperties jwtProperties;
  private final UserDetailsService userDetailsService;

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

    // CSRF Configuration:
    // We use a cookie-based repository. Crucially, 'withHttpOnlyFalse' is used, so the Angular
    // frontend can read the CSRF token from the cookie and include it in the 'X-XSRF-TOKEN' header.
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(builder -> builder.secure(true).sameSite("Strict"));

    // The RequestAttributeHandler makes the CSRF token available as a request attribute,
    // which is required for the XorCsrfTokenRequestAttributeHandler default in Spring Security 6.
    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

    http.cors(withDefaults()) // Delegate to the corsConfigurationSource bean
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(requestHandler)
                    // Exclude public authentication endpoints and actuators from CSRF checks
                    .ignoringRequestMatchers(
                        "/antares/auth/register",
                        "/antares/auth/login",
                        "/antares/auth/refresh-token",
                        "/actuator/**"))
        .authorizeHttpRequests(
            auth ->
                auth
                    // Secure Documentation and Admin endpoints (ADMIN only)
                    .requestMatchers("/antares-docs", "/antares-docs/**")
                    .permitAll()
                    // Allow public access to Authentication & Health endpoints
                    .requestMatchers("/antares/auth/**", "/actuator/**")
                    .permitAll()
                    // Require authentication for all other endpoints
                    .anyRequest()
                    .authenticated())
        .sessionManagement(
            session ->
                session
                    // Enforce statelessness; no HttpSession will be created or used
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokenResolver())
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  /**
   * Configures a custom {@link BearerTokenResolver} to extract the access token.
   *
   * <p>Strategy:
   *
   * <ol>
   *   <li>Attempt to find the token in the configured access token Cookie.
   *   <li>Fallback to the standard Authorization header (Bearer schema) for API clients.
   * </ol>
   *
   * @return The token resolver instance.
   */
  @Bean
  public BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    return request -> {
      // 1. Check Cookies
      if (request.getCookies() != null) {
        return Arrays.stream(request.getCookies())
            .filter(c -> jwtProperties.accessToken().name().equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElseGet(() -> headerResolver.resolve(request));
      }
      // 2. Fallback to Header
      return headerResolver.resolve(request);
    };
  }

  /**
   * Configures a converter to transform a raw JWT into an authenticated Principal.
   *
   * <p>This implementation extracts the subject (email) from the JWT and retrieves {@link User}
   * entity via {@link UserDetailsService}.
   *
   * <p><b>Performance Note:</b> The user details lookup is cached (e.g., in Redis) as configured in
   * {@link ApplicationConfig}. This architecture avoids a database round-trip for every request
   * while ensuring the Principal reflects the user's up-to-date state (thanks to cache eviction on
   * updates).
   *
   * @return A converter from {@link Jwt} to {@link AbstractAuthenticationToken}.
   */
  private Converter<@NonNull Jwt, @NonNull AbstractAuthenticationToken>
      jwtAuthenticationConverter() {
    return jwt -> {
      String email = jwt.getSubject();
      User user = (User) userDetailsService.loadUserByUsername(email);
      return new UsernamePasswordAuthenticationToken(user, jwt, user.getAuthorities());
    };
  }

  /**
   * Configures the {@link JwtDecoder} for verifying incoming tokens.
   *
   * <p><b>Security Features:</b>
   *
   * <ul>
   *   <li>Signature verification (HMAC-SHA256).
   *   <li>Expiration check (exp, nbf).
   *   <li>Issuer validation (must match 'antares-auth').
   *   <li>Audience validation (must contain 'sirius-app').
   * </ul>
   *
   * @return The configured JWT decoder.
   */
  @Bean
  public JwtDecoder jwtDecoder() {

    SecretKeySpec secretKey =
        new SecretKeySpec(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();

    // 1. Standard Validator (checks 'exp', 'nbf', and 'iss')
    OAuth2TokenValidator<Jwt> withIssuer =
        JwtValidators.createDefaultWithIssuer(jwtProperties.issuer());

    // 2. Custom Audience Validator (checks 'aud')
    OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(jwtProperties.audience());

    // 3. Combine Validators
    OAuth2TokenValidator<Jwt> combinedValidator =
        new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);

    decoder.setJwtValidator(combinedValidator);

    return decoder;
  }

  /**
   * Configures the {@link JwtEncoder} for signing outgoing tokens.
   *
   * @return The configured JWT encoder.
   */
  @Bean
  public JwtEncoder jwtEncoder() {

    return new NimbusJwtEncoder(
        new ImmutableSecret<>(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8)));
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

  /**
   * Custom Validator to verify the 'aud' (Audience) claim. Ensures the token is intended for this
   * application.
   */
  private record AudienceValidator(String audience) implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
      if (jwt.getAudience().contains(audience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "The required audience is missing", null));
    }
  }
}
