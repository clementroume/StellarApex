package apex.stellar.antares.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.service.JwtService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for unauthenticated vs. forbidden requests.
 *
 * <p>Sirius only refreshes its tokens on a 401, so a missing or expired access token must answer
 * 401 (not 403), while an authenticated user without the required rights keeps getting 403.
 */
class AuthenticationEntryPointIT extends BaseIntegrationTest {

  private static final String PASSWORD = "password123";

  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtService jwtService;
  @Autowired private JwtProperties jwtProperties;

  @BeforeEach
  void setUp() {
    userRepository.deleteAllInBatch(
        userRepository.findAll().stream()
            .filter(u -> !u.getPlatformRole().name().equals("ADMIN"))
            .toList());
  }

  @Test
  @DisplayName("Anonymous request to a protected endpoint should be a localized 401 ProblemDetail")
  void anonymous_shouldBeUnauthorized() throws Exception {
    mockMvc
        .perform(get("/antares/users/me").header("Accept-Language", "fr"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.detail").value("Authentification requise."))
        .andExpect(jsonPath("$.instance").value("/antares/users/me"))
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName("Expired access token should be 401, then a refresh should restore access")
  void expiredAccessToken_shouldBeUnauthorizedThenRefresh() throws Exception {
    Cookie[] loginCookies = registerAndLogin("expired@test.com", PASSWORD);
    Cookie refreshCookie = cookie(loginCookies, jwtService.getRefreshTokenCookieName());
    Cookie expiredAccess =
        new Cookie(jwtService.getAccessTokenCookieName(), expiredToken("expired@test.com"));

    mockMvc
        .perform(get("/antares/users/me").cookie(expiredAccess, refreshCookie))
        .andExpect(status().isUnauthorized());

    MvcResult refresh =
        mockMvc
            .perform(post("/antares/auth/refresh-token").cookie(expiredAccess, refreshCookie))
            .andExpect(status().isOk())
            .andReturn();
    Cookie newAccess =
        cookie(refresh.getResponse().getCookies(), jwtService.getAccessTokenCookieName());

    mockMvc
        .perform(get("/antares/users/me").cookie(newAccess))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("expired@test.com"));
  }

  @Test
  @DisplayName("Authenticated user without the required role should still be 403")
  void authenticatedWithoutRights_shouldBeForbidden() throws Exception {
    Cookie[] cookies = registerAndLogin("user@test.com", PASSWORD);

    mockMvc
        .perform(get("/antares/admin/users").cookie(cookies))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("Anonymous write without CSRF token should still be 403")
  void anonymousWriteWithoutCsrf_shouldBeForbidden() throws Exception {
    mockMvc
        .perform(post("/antares/gyms").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
  }

  /** Builds a correctly signed access token that expired five minutes ago. */
  private String expiredToken(String email) {
    Instant expiredAt = Instant.now().minus(5, ChronoUnit.MINUTES);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .audience(List.of(jwtProperties.audience()))
            .issuedAt(expiredAt.minus(15, ChronoUnit.MINUTES))
            .expiresAt(expiredAt)
            .subject(email)
            .id(UUID.randomUUID().toString())
            .claim("scope", "ROLE_USER")
            .build();
    return jwtEncoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private static Cookie cookie(Cookie[] cookies, String name) {
    return Stream.of(cookies)
        .filter(c -> name.equals(c.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(name + " cookie not found in " + Arrays.toString(cookies)));
  }
}
