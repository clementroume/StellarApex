package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.config.JwtProperties;
import apex.stellar.antares.dto.ChangePasswordRequest;
import apex.stellar.antares.dto.SupportSessionRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.Permission;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.SupportSession;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import apex.stellar.antares.repository.jpa.MembershipRepository;
import apex.stellar.antares.repository.jpa.SupportSessionRepository;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Integration tests of the admin support session lifecycle and the admin user search. */
class SupportSessionIT extends BaseIntegrationTest {

  private static final String PASSWORD = "password123";
  private static final String SUPPORT_COOKIE = "support_token";
  private static final String SESSIONS_URL = "/antares/admin/support-sessions";

  @Autowired private GymRepository gymRepository;
  @Autowired private MembershipRepository membershipRepository;
  @Autowired private SupportSessionRepository supportSessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtProperties jwtProperties;

  private User admin;
  private User target;
  private Gym gym;
  private Cookie[] adminCookies;

  @BeforeEach
  void setUp() throws Exception {
    supportSessionRepository.deleteAll();
    membershipRepository.deleteAll();
    gymRepository.deleteAll();
    userRepository.deleteAll();

    admin = createAdmin("admin@test.com", PASSWORD);
    adminCookies = login("admin@test.com", PASSWORD);

    target = createUser("Jane", "Doe", "jane.doe@test.com", PlatformRole.USER);
    gym =
        gymRepository.save(
            Gym.builder()
                .name("Support Box")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("SUP")
                .build());
    membershipRepository.save(
        Membership.builder()
            .user(target)
            .gym(gym)
            .gymRole(GymRole.COACH)
            .status(MembershipStatus.ACTIVE)
            .permissions(Set.of(Permission.WOD_WRITE))
            .build());
  }

  // ==================================================================================
  // 1. LIFECYCLE
  // ==================================================================================

  @Test
  @DisplayName("Start: Admin should get 201, a strict 30 min cookie and keep its own cookies")
  void start_shouldCreateSessionAndCookie() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withXsrf(post(SESSIONS_URL), adminCookies)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(target.getId(), "Ticket #42")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.actor.id").value(admin.getId()))
            .andExpect(jsonPath("$.actor.firstName").value("Admin"))
            .andExpect(jsonPath("$.target.id").value(target.getId()))
            .andExpect(jsonPath("$.target.email").value("jane.doe@test.com"))
            .andExpect(jsonPath("$.reason").value("Ticket #42"))
            .andExpect(jsonPath("$.startedAt").exists())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andReturn();

    String setCookie =
        result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(c -> c.startsWith(SUPPORT_COOKIE + "="))
            .findFirst()
            .orElseThrow();
    assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Max-Age=1800");
    // Secure follows the cookie configuration like the session cookies (the CI turns it off)
    if (jwtProperties.cookie().secure()) {
      assertThat(setCookie).contains("Secure");
    } else {
      assertThat(setCookie).doesNotContain("Secure");
    }
    // The admin's own session cookies are untouched
    assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
        .noneMatch(c -> c.startsWith("stellar_access_token") || c.startsWith("stellar_refresh"));

    SupportSession session = supportSessionRepository.findAll().getFirst();
    assertThat(session.getAdminId()).isEqualTo(admin.getId());
    assertThat(session.getTargetUserId()).isEqualTo(target.getId());
    assertThat(session.getEndedAt()).isNull();
    assertThat(session.getExpiresAt()).isEqualTo(session.getStartedAt().plusSeconds(1800));
  }

  @Test
  @DisplayName("During session: /me returns the target with supportSession, verifyApi the target")
  void duringSession_shouldActAsTarget() throws Exception {
    Cookie[] cookies = startSession();

    mockMvc
        .perform(get("/antares/users/me").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(target.getId()))
        .andExpect(jsonPath("$.email").value("jane.doe@test.com"))
        .andExpect(jsonPath("$.memberships[0].gymRole").value("COACH"))
        .andExpect(jsonPath("$.memberships[0].gymName").value("Support Box"))
        .andExpect(jsonPath("$.supportSession.id").isNumber())
        .andExpect(jsonPath("$.supportSession.actor.id").value(admin.getId()))
        .andExpect(jsonPath("$.supportSession.actor.lastName").value("User"))
        .andExpect(jsonPath("$.supportSession.reason").value("Ticket #42"))
        .andExpect(jsonPath("$.supportSession.expiresAt").exists());

    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Id", target.getId().toString()))
        .andExpect(header().string("X-Auth-User-Role", "USER"))
        .andExpect(header().string("X-Auth-User-Locale", "en"))
        .andExpect(header().string("X-Auth-Memberships", gym.getId() + ":COACH:WOD_WRITE"))
        .andExpect(header().string("X-Auth-Actor-Id", admin.getId().toString()))
        .andExpect(header().string("X-Internal-Secret", "test-internal-secret"));

    // Admin-only endpoints follow the target's rights
    mockMvc
        .perform(get("/antares/admin/users").cookie(cookies))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Outside a session: /me has no supportSession and verifyApi no actor")
  void outsideSession_shouldBeAdmin() throws Exception {
    mockMvc
        .perform(get("/antares/users/me").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("admin@test.com"))
        .andExpect(jsonPath("$.supportSession").isEmpty());

    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Role", "ADMIN"))
        .andExpect(header().doesNotExist("X-Auth-Actor-Id"));
  }

  @Test
  @DisplayName("During session: profile updates keep the session, preferences are never saved")
  void duringSession_profileUpdatesKeepSupportSession() throws Exception {
    Cookie[] cookies = startSession();

    // Language and theme stay on the admin's screen: the target's account must not change
    mockMvc
        .perform(
            withXsrf(patch("/antares/users/me/preferences"), cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"fr\",\"theme\":\"dark\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_FORBIDDEN"));
    assertThat(userRepository.findById(target.getId()).orElseThrow().getLocale()).isEqualTo("en");

    mockMvc
        .perform(
            withXsrf(put("/antares/users/me/profile"), cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"firstName\":\"Janet\",\"lastName\":\"Doe\",\"email\":\"jane.doe@test.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Janet"))
        .andExpect(jsonPath("$.supportSession.actor.id").value(admin.getId()));

    // Outside a session, the same endpoints carry no support session
    mockMvc
        .perform(
            withXsrf(patch("/antares/users/me/preferences"), adminCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"en\",\"theme\":\"light\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.supportSession").isEmpty());
  }

  @Test
  @DisplayName("During session: password change, account deletion and new session are refused")
  void duringSession_sensitiveOperationsForbidden() throws Exception {
    Cookie[] cookies = startSession();
    ChangePasswordRequest password = new ChangePasswordRequest(PASSWORD, "newPass1234", "newPass1234");

    mockMvc
        .perform(
            withXsrf(put("/antares/users/me/password"), cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(password)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_FORBIDDEN"));

    mockMvc
        .perform(withXsrf(delete("/antares/users/me"), cookies))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_FORBIDDEN"));

    mockMvc
        .perform(
            withXsrf(post(SESSIONS_URL), cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(target.getId(), "Again")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_FORBIDDEN"));

    assertThat(userRepository.findById(target.getId())).isPresent();
    assertThat(supportSessionRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Second session while one is active should be a conflict (409)")
  void start_whenActive_shouldConflict() throws Exception {
    startSession();

    mockMvc
        .perform(
            withXsrf(post(SESSIONS_URL), adminCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(target.getId(), "Again")))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Expired session: 401 SUPPORT_SESSION_EXPIRED, cookie cleared, no admin fallback")
  void expired_shouldRejectWithoutFallback() throws Exception {
    Cookie[] cookies = startSession();
    SupportSession session = supportSessionRepository.findAll().getFirst();
    // expires_at is not updatable through JPA (audit record)
    jdbcTemplate.update(
        "UPDATE antares.support_sessions SET expires_at = ? WHERE id = ?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        session.getId());

    MvcResult result =
        mockMvc
            .perform(get("/antares/users/me").cookie(cookies))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_EXPIRED"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andReturn();
    assertSupportCookieCleared(result);

    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_EXPIRED"))
        .andExpect(header().doesNotExist("X-Auth-User-Id"));

    assertThat(supportSessionRepository.findById(session.getId()).orElseThrow().getEndedAt())
        .isNotNull();
  }

  @Test
  @DisplayName("Support cookie of another admin should be rejected (401)")
  void otherAdmin_shouldBeRejected() throws Exception {
    Cookie supportCookie = supportCookie(startSession());
    createAdmin("admin2@test.com", PASSWORD);
    Cookie[] otherAdmin = login("admin2@test.com", PASSWORD);

    mockMvc
        .perform(get("/antares/users/me").cookie(with(otherAdmin, supportCookie)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_EXPIRED"));
  }

  @Test
  @DisplayName("Support cookie without a valid access token should stay anonymous")
  void supportCookieAlone_shouldBeAnonymous() throws Exception {
    Cookie supportCookie = supportCookie(startSession());

    // Anonymous requests to protected endpoints are refused (plain 401, as without any cookie)
    mockMvc
        .perform(get("/antares/users/me").cookie(supportCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist());
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(supportCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("X-Auth-User-Id"));
  }

  @Test
  @DisplayName("End: 204, session ended, cookie cleared; idempotent; old cookie then rejected")
  void end_shouldEndSessionIdempotently() throws Exception {
    Cookie[] cookies = startSession();

    MvcResult result =
        mockMvc
            .perform(withXsrf(delete(SESSIONS_URL + "/current"), cookies))
            .andExpect(status().isNoContent())
            .andReturn();
    assertSupportCookieCleared(result);
    assertThat(supportSessionRepository.findAll().getFirst().getEndedAt()).isNotNull();

    // Idempotent, with or without the stale cookie
    mockMvc
        .perform(withXsrf(delete(SESSIONS_URL + "/current"), cookies))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(withXsrf(delete(SESSIONS_URL + "/current"), adminCookies))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/antares/users/me").cookie(cookies))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_EXPIRED"));
  }

  @Test
  @DisplayName("Logout should end the active support session")
  void logout_shouldEndSession() throws Exception {
    Cookie[] cookies = startSession();

    MvcResult result =
        mockMvc
            .perform(withXsrf(post("/antares/auth/logout"), cookies))
            .andExpect(status().isOk())
            .andReturn();

    assertSupportCookieCleared(result);
    assertThat(supportSessionRepository.findAll().getFirst().getEndedAt()).isNotNull();
  }

  // ==================================================================================
  // 2. START REFUSALS
  // ==================================================================================

  @Test
  @DisplayName("Start: Non-admin should be forbidden (403)")
  void start_nonAdmin_shouldBeForbidden() throws Exception {
    Cookie[] userCookies = registerAndLogin("user@test.com", PASSWORD);

    mockMvc
        .perform(
            withXsrf(post(SESSIONS_URL), userCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(target.getId(), "Nope")))
        .andExpect(status().isForbidden());
    assertThat(supportSessionRepository.count()).isZero();
  }

  @Test
  @DisplayName("Start: Admin target should be forbidden (403)")
  void start_adminTarget_shouldBeForbidden() throws Exception {
    User otherAdmin = createAdmin("admin3@test.com", PASSWORD);

    mockMvc
        .perform(
            withXsrf(post(SESSIONS_URL), adminCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(otherAdmin.getId(), "Nope")))
        .andExpect(status().isForbidden());
    assertThat(supportSessionRepository.count()).isZero();
  }

  @Test
  @DisplayName("Start: Unknown user should be not found (404)")
  void start_unknownUser_shouldBeNotFound() throws Exception {
    mockMvc
        .perform(
            withXsrf(post(SESSIONS_URL), adminCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(999_999L, "Nope")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Start: Blank or too long reason should be a bad request (400)")
  void start_invalidReason_shouldBeBadRequest() throws Exception {
    for (String reason : new String[] {" ", "x".repeat(256)}) {
      mockMvc
          .perform(
              withXsrf(post(SESSIONS_URL), adminCookies)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(target.getId(), reason)))
          .andExpect(status().isBadRequest());
    }
  }

  @Test
  @DisplayName("Start: Missing XSRF token should be forbidden")
  void start_withoutXsrf_shouldBeForbidden() throws Exception {
    mockMvc
        .perform(
            post(SESSIONS_URL)
                .cookie(adminCookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(target.getId(), "Ticket")))
        .andExpect(status().isForbidden());
  }

  // ==================================================================================
  // 3. ADMIN USER SEARCH
  // ==================================================================================

  @Test
  @DisplayName("Search: Should match first name, last name or email, case-insensitive")
  void search_shouldMatchNamesAndEmail() throws Exception {
    createUser("John", "Smith", "jsmith@test.com", PlatformRole.USER);
    createUser("Anna", "Doering", "anna@test.com", PlatformRole.USER);

    mockMvc
        .perform(get("/antares/admin/users").param("search", "DOE").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].lastName").value("Doe"))
        .andExpect(jsonPath("$.content[0].memberships[0].gymName").value("Support Box"))
        .andExpect(jsonPath("$.content[1].lastName").value("Doering"));

    mockMvc
        .perform(get("/antares/admin/users").param("search", "john").cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("jsmith@test.com"));

    mockMvc
        .perform(get("/antares/admin/users").param("search", "ANNA@TEST").cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(1));

    // LIKE wildcards are matched literally
    mockMvc
        .perform(get("/antares/admin/users").param("search", "%").cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("Search: Should paginate by 20 users ordered by last name")
  void search_shouldPaginateBy20() throws Exception {
    for (int i = 0; i < 25; i++) {
      createUser("Bulk", String.format("Name%02d", 24 - i), "bulk" + i + "@test.com",
          PlatformRole.USER);
    }

    mockMvc
        .perform(get("/antares/admin/users").param("search", "bulk").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(20))
        .andExpect(jsonPath("$.content[0].lastName").value("Name00"))
        .andExpect(jsonPath("$.content[19].lastName").value("Name19"))
        .andExpect(jsonPath("$.last").value(false));

    mockMvc
        .perform(
            get("/antares/admin/users").param("search", "bulk").param("page", "1").cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.content[0].lastName").value("Name20"))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test
  @DisplayName("Search: Should filter on a membership's gym, role and status together")
  void search_shouldFilterOnMembership() throws Exception {
    User banned = createUser("Ban", "Ned", "banned@test.com", PlatformRole.USER);
    membershipRepository.save(
        Membership.builder()
            .user(banned)
            .gym(gym)
            .gymRole(GymRole.ATHLETE)
            .status(MembershipStatus.BANNED)
            .build());
    createUser("No", "Gym", "nogym@test.com", PlatformRole.USER);

    mockMvc
        .perform(
            get("/antares/admin/users").param("gymId", gym.getId().toString()).cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(2));
    mockMvc
        .perform(get("/antares/admin/users").param("role", "COACH").cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("jane.doe@test.com"));
    mockMvc
        .perform(
            get("/antares/admin/users")
                .param("gymId", gym.getId().toString())
                .param("status", "BANNED")
                .cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].email").value("banned@test.com"));
    // Role and status must hold on the same membership
    mockMvc
        .perform(
            get("/antares/admin/users")
                .param("role", "COACH")
                .param("status", "BANNED")
                .cookie(adminCookies))
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("Lookup: Should resolve ids into summaries, ignoring unknown ids")
  void lookup_shouldResolveSummaries() throws Exception {
    mockMvc
        .perform(
            get("/antares/admin/users/lookup")
                .param("ids", target.getId() + ",999999")
                .cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].email").value("jane.doe@test.com"))
        .andExpect(jsonPath("$[0].memberships").doesNotExist());
  }

  @Test
  @DisplayName("Recent: Should list the last targets once each, latest first")
  void recent_shouldListLastTargets() throws Exception {
    User other = createUser("Max", "Other", "max@test.com", PlatformRole.USER);
    Instant now = Instant.now();
    for (Object[] row :
        new Object[][] {
          {target, now.minusSeconds(300)}, {other, now.minusSeconds(200)}, {target, now.minusSeconds(100)}
        }) {
      supportSessionRepository.save(
          SupportSession.builder()
              .adminId(admin.getId())
              .targetUserId(((User) row[0]).getId())
              .reason("Audit")
              .startedAt((Instant) row[1])
              .expiresAt(((Instant) row[1]).plusSeconds(60))
              .endedAt(((Instant) row[1]).plusSeconds(30))
              .build());
    }

    mockMvc
        .perform(get("/antares/admin/support-sessions/recent").cookie(adminCookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].email").value("jane.doe@test.com"))
        .andExpect(jsonPath("$[0].memberships[0].gymName").value("Support Box"))
        .andExpect(jsonPath("$[1].email").value("max@test.com"));
  }

  @Test
  @DisplayName("Search: Non-admin should be forbidden (403)")
  void search_nonAdmin_shouldBeForbidden() throws Exception {
    Cookie[] userCookies = registerAndLogin("user@test.com", PASSWORD);

    mockMvc
        .perform(get("/antares/admin/users").param("search", "doe").cookie(userCookies))
        .andExpect(status().isForbidden());
  }

  // --- Helpers ---

  /** Starts a session as {@link #target} and returns the admin cookies plus the support cookie. */
  private Cookie[] startSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                withXsrf(post(SESSIONS_URL), adminCookies)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(target.getId(), "Ticket #42")))
            .andExpect(status().isCreated())
            .andReturn();
    Cookie support = result.getResponse().getCookie(SUPPORT_COOKIE);
    assertThat(support).isNotNull();
    return with(adminCookies, support);
  }

  private Cookie supportCookie(Cookie[] cookies) {
    return Arrays.stream(cookies)
        .filter(c -> SUPPORT_COOKIE.equals(c.getName()))
        .findFirst()
        .orElseThrow();
  }

  private static Cookie[] with(Cookie[] cookies, Cookie extra) {
    return Stream.concat(Arrays.stream(cookies), Stream.of(extra)).toArray(Cookie[]::new);
  }

  private MockHttpServletRequestBuilder withXsrf(
      MockHttpServletRequestBuilder builder, Cookie[] cookies) {
    return builder.cookie(cookies).header("X-XSRF-TOKEN", getXsrfToken(cookies));
  }

  private String body(Long userId, String reason) throws Exception {
    return jsonMapper.writeValueAsString(new SupportSessionRequest(userId, reason));
  }

  private void assertSupportCookieCleared(MvcResult result) {
    assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
        .anyMatch(c -> c.startsWith(SUPPORT_COOKIE + "=;") && c.contains("Max-Age=0"));
  }

  private User createUser(String firstName, String lastName, String email, PlatformRole role) {
    return userRepository.save(
        User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .password(passwordEncoder.encode(PASSWORD))
            .platformRole(role)
            .build());
  }
}
