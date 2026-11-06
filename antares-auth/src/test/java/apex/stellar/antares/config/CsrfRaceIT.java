package apex.stellar.antares.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.dto.ProfileUpdateRequest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The {@code XSRF-TOKEN} cookie stays stable, and a CSRF rejection is a 403 ProblemDetail.
 *
 * <p>Before the fix, every request authenticated by the JWT filter rotated the cookie (Spring's
 * {@code CsrfAuthenticationStrategy} ran because no session holds the authentication): a GET
 * answered while a POST was in flight made the POST's header stale, the POST was rejected with
 * {@code sendError(403)} and the {@code /error} dispatch came back as an unauthenticated 401.
 */
class CsrfRaceIT extends BaseIntegrationTest {

  private Cookie[] cookies;
  private String firstToken;

  @BeforeEach
  void setUp() throws Exception {
    cookies = registerAndLogin("race@example.com", "password123");
    firstToken = getXsrfToken(cookies);
  }

  @Test
  @DisplayName("A GET does not rewrite the XSRF-TOKEN, so an in-flight POST keeps its token")
  void getDoesNotRotateToken() throws Exception {
    MvcResult get = mockMvc.perform(get("/antares/users/me").secure(true).cookie(cookies)).andReturn();

    assertThat(get.getResponse().getCookie("XSRF-TOKEN")).isNull();

    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "race@example.com");
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .secure(true)
                .cookie(cookies)
                .header("X-XSRF-TOKEN", firstToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("A stale token is a 403 ProblemDetail CSRF_INVALID, localized")
  void staleTokenIsLocalizedForbidden() throws Exception {
    ProfileUpdateRequest request = new ProfileUpdateRequest("John", "Doe", "race@example.com");
    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .secure(true)
                .cookie(cookies)
                .header("X-XSRF-TOKEN", "stale-token")
                .header("Accept-Language", "fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
        .andExpect(jsonPath("$.instance").value("/antares/users/me/profile"))
        .andExpect(jsonPath("$.detail").value("Votre jeton de sécurité n'est plus valide. Rechargez la page ou réessayez."));
  }

  @Test
  @DisplayName("GET /auth/csrf is public and sets the cookie when the browser has none")
  void csrfEndpointSetsCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/antares/auth/csrf").secure(true)).andExpect(status().isNoContent()).andReturn();

    assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
  }

  /** Value of the last XSRF-TOKEN Set-Cookie of a response, or null when it does not touch it. */
  private static String lastXsrf(MvcResult result) {
    return result.getResponse().getHeaders("Set-Cookie").stream()
        .filter(c -> c.startsWith("XSRF-TOKEN=") && !c.startsWith("XSRF-TOKEN=;"))
        .reduce((a, b) -> b)
        .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
        .orElse(null);
  }

  @Test
  @DisplayName("The token rotates at login, logout, and support session start/end, never on refresh")
  void tokenRotatesOnIdentityChangesOnly() throws Exception {
    // login: the cookie sent before differs from the one issued
    MvcResult login =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .secure(true)
                    .cookie(new Cookie("XSRF-TOKEN", "pre-login"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"race@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(lastXsrf(login)).isNotNull().isNotEqualTo("pre-login");

    // refresh keeps it
    MvcResult refresh =
        mockMvc
            .perform(post("/antares/auth/refresh-token").secure(true).cookie(cookies))
            .andReturn();
    assertThat(lastXsrf(refresh)).isNull();

    // logout rotates
    MvcResult logout =
        mockMvc
            .perform(
                post("/antares/auth/logout")
                    .secure(true)
                    .cookie(cookies)
                    .header("X-XSRF-TOKEN", firstToken))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(lastXsrf(logout)).isNotNull().isNotEqualTo(firstToken);
  }

  @Test
  @DisplayName("The token rotates when an admin support session starts and ends")
  void tokenRotatesOnSupportSession() throws Exception {
    createAdmin("race.admin@example.com", "password123");
    Cookie[] adminCookies = login("race.admin@example.com", "password123");
    String adminToken = getXsrfToken(adminCookies);
    Long targetId = userRepository.findByEmail("race@example.com").orElseThrow().getId();

    MvcResult start =
        mockMvc
            .perform(
                post("/antares/admin/support-sessions")
                    .secure(true)
                    .cookie(adminCookies)
                    .header("X-XSRF-TOKEN", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + targetId + ",\"reason\":\"csrf test\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String afterStart = lastXsrf(start);
    assertThat(afterStart).isNotNull().isNotEqualTo(adminToken);

    MvcResult end =
        mockMvc
            .perform(
                delete("/antares/admin/support-sessions/current")
                    .secure(true)
                    .cookie(
                        java.util.stream.Stream.concat(
                                java.util.Arrays.stream(adminCookies)
                                    .filter(c -> !"XSRF-TOKEN".equals(c.getName())),
                                java.util.stream.Stream.of(new Cookie("XSRF-TOKEN", afterStart)))
                            .toArray(Cookie[]::new))
                    .header("X-XSRF-TOKEN", afterStart))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(lastXsrf(end)).isNotNull().isNotEqualTo(afterStart);
  }
}
