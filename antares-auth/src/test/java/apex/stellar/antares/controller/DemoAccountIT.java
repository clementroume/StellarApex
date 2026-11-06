package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.exception.DemoAccountException;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Integration tests of roadmap D68: a demo account (public password) can never be a platform
 * administrator, on any path - requests, JPA, the gateway, the admin endpoints and the database.
 */
class DemoAccountIT extends BaseIntegrationTest {

  private static final String PASSWORD = "password123";
  private static final String DEMO_EMAIL = "owner.sparta@demo.test";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder encoder;

  private User demo;

  @BeforeEach
  void setUp() {
    demo =
        userRepository.save(
            User.builder()
                .firstName("Demo")
                .lastName("User")
                .email(DEMO_EMAIL)
                .password(encoder.encode(PASSWORD))
                .platformRole(PlatformRole.USER)
                .emailVerified(true)
                .demo(true)
                .build());
  }

  // --- Requests cannot create or promote anyone ---

  @Test
  @DisplayName("register: role and demo flag sent by the client are ignored")
  void register_IgnoresRoleAndDemo() throws Exception {
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Eve","lastName":"Evil","email":"eve@test.com",
                     "password":"password123","platformRole":"ADMIN","demo":true,"role":"ADMIN"}
                    """))
        .andExpect(status().isCreated());

    User eve = userRepository.findByEmail("eve@test.com").orElseThrow();
    assertThat(eve.getPlatformRole()).isEqualTo(PlatformRole.USER);
    assertThat(eve.isDemo()).isFalse();
  }

  @Test
  @DisplayName("register: an address of the reserved .test domain is refused")
  void register_ReservedDomainIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Eve","lastName":"Evil","email":"Owner@Sparta.TEST",
                     "password":"password123"}
                    """))
        .andExpect(status().isBadRequest());

    assertThat(userRepository.findByEmail("Owner@Sparta.TEST")).isEmpty();
  }

  @Test
  @DisplayName("profile and preference updates cannot change the role nor the demo flag")
  void updates_CannotChangeRoleOrDemo() throws Exception {
    Cookie[] cookies = login(DEMO_EMAIL, PASSWORD);
    String xsrf = getXsrfToken(cookies);

    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .cookie(cookies)
                .header("X-XSRF-TOKEN", xsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Demo","lastName":"User","email":"owner.sparta@demo.test",
                     "platformRole":"ADMIN","demo":false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformRole").value("USER"));
    mockMvc
        .perform(
            patch("/antares/users/me/preferences")
                .cookie(cookies)
                .header("X-XSRF-TOKEN", xsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"en\",\"theme\":\"dark\",\"platformRole\":\"ADMIN\",\"demo\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.platformRole").value("USER"));

    User stored = userRepository.findByEmail(DEMO_EMAIL).orElseThrow();
    assertThat(stored.getPlatformRole()).isEqualTo(PlatformRole.USER);
    assertThat(stored.isDemo()).isTrue();
  }

  // --- Forward-auth gateways ---

  @Test
  @DisplayName("users/me: tells the client the account is a demo account")
  void me_ExposesDemoFlag() throws Exception {
    Cookie[] cookies = login(DEMO_EMAIL, PASSWORD);
    mockMvc
        .perform(get("/antares/users/me").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.demo").value(true));
  }

  @Test
  @DisplayName("verify/api: a demo account is sent as USER")
  void verifyApi_SendsUser() throws Exception {
    Cookie[] cookies = login(DEMO_EMAIL, PASSWORD);

    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Role", "USER"));
  }

  @Test
  @DisplayName("verify/admin: a demo account is forbidden")
  void verifyAdmin_IsForbidden() throws Exception {
    Cookie[] cookies = login(DEMO_EMAIL, PASSWORD);

    mockMvc
        .perform(get("/antares/auth/verify/admin").cookie(cookies))
        .andExpect(status().isForbidden());
  }

  // --- Admin endpoints ---

  @Test
  @DisplayName("admin endpoints: a demo account cannot list users nor start a support session")
  void adminEndpoints_AreForbidden() throws Exception {
    Cookie[] cookies = login(DEMO_EMAIL, PASSWORD);
    String xsrf = getXsrfToken(cookies);
    User other = createAdmin("someone@test.com", PASSWORD);

    mockMvc
        .perform(get("/antares/admin/users").cookie(cookies))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/antares/admin/support-sessions")
                .cookie(cookies)
                .header("X-XSRF-TOKEN", xsrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + other.getId() + ",\"reason\":\"demo\"}"))
        .andExpect(status().isForbidden());
  }

  // --- Application (JPA) ---

  @Test
  @DisplayName("JPA: promoting a demo account to ADMIN is refused and nothing is stored")
  void jpa_PromotingDemoIsRefused() {
    assertThatThrownBy(() -> demo.setPlatformRole(PlatformRole.ADMIN))
        .isInstanceOf(DemoAccountException.class);

    assertThat(userRepository.findByEmail(DEMO_EMAIL).orElseThrow().getPlatformRole())
        .isEqualTo(PlatformRole.USER);
  }

  @Test
  @DisplayName("JPA: saving a demo ADMIN built by hand is refused")
  void jpa_SavingDemoAdminIsRefused() {
    User tampered =
        User.builder()
            .email("tampered.sparta@demo.test")
            .password("x")
            .platformRole(PlatformRole.ADMIN)
            .demo(true)
            .build();

    assertThatThrownBy(() -> userRepository.saveAndFlush(tampered))
        .isInstanceOf(DemoAccountException.class);
    assertThat(userRepository.findByEmail("tampered.sparta@demo.test")).isEmpty();
  }

  @Test
  @DisplayName("JPA: turning an administrator into a demo account is refused")
  void jpa_DemotingAdminToDemoIsRefused() {
    User admin = createAdmin("admin@test.com", PASSWORD);

    assertThatThrownBy(() -> admin.setDemo(true)).isInstanceOf(DemoAccountException.class);
    assertThat(userRepository.findByEmail("admin@test.com").orElseThrow().isDemo()).isFalse();
  }

  // --- Database ---

  @Test
  @DisplayName("database: the CHECK refuses ADMIN for a demo account (update, insert, flag)")
  void database_CheckRefusesDemoAdmin() {
    assertThatThrownBy(
            () -> jdbc.update("UPDATE antares.users SET platform_role = 'ADMIN' WHERE id = ?", demo.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO antares.users (email, password, platform_role, demo)"
                        + " VALUES ('raw.sparta@demo.test', 'x', 'ADMIN', TRUE)"))
        .isInstanceOf(DataIntegrityViolationException.class);

    createAdmin("admin@test.com", PASSWORD);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE antares.users SET demo = TRUE WHERE email = 'admin@test.com'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
