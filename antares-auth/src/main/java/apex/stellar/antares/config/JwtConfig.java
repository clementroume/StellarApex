package apex.stellar.antares.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

/**
 * Configuration class for setting up JWT encoding and decoding processes.
 *
 * <p>This configuration integrates with the Spring Security framework to provide functions for
 * validating and issuing JWT tokens. It leverages HMAC-SHA256 for cryptographic signing and
 * supports customizable validation logic for claims such as expiration, issuer, and audience.
 *
 * <p>Dependencies: - {@link JwtProperties}: Holds the configurable properties for JWT tokens.
 *
 * <p>Security measures implemented: - Signature verification using a secret key. - Validation of
 * token expiration and "not before" claims. - Ensures the token is issued by a trusted authority
 * defined by the issuer. - Validates the audience to restrict token usage to the intended
 * applications.
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

  private final JwtProperties jwtProperties;

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
