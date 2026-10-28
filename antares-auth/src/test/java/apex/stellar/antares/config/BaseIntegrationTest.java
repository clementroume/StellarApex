package apex.stellar.antares.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

  @ServiceConnection(name = "redis")
  @SuppressWarnings("resource")
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

  static {
    postgres.start();
    redis.start();
  }

  @Autowired protected StringRedisTemplate redisTemplate;
  @Autowired protected MockMvc mockMvc;
  @Autowired protected JsonMapper jsonMapper;
  @Autowired protected UserRepository userRepository;
  @Autowired protected PasswordEncoder passwordEncoder;

  /** No SMTP server in tests: sent messages can be captured from this mock. */
  @MockitoBean protected JavaMailSender mailSender;

  @DynamicPropertySource
  static void registerCustomProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "ANTARES_JWT_SECRET",
        () ->
            "YjQ1ZGRjYjU5YjYwNzZkMWY2MzE4YmFiY2Y4ZjgxMGE0YzY4ZmIwYmZkOTRkMjYxYmVjZGU1Y2Y3YWQyYjQzYw==");
    registry.add("application.admin.default-firstname", () -> "Test");
    registry.add("application.admin.default-lastname", () -> "Admin");
    registry.add("application.admin.default-email", () -> "admin.test@antares.com");
    registry.add("application.admin.default-password", () -> "testPassword123!");
    registry.add("application.security.jwt.issuer", () -> "antares-test-issuer");
    registry.add("application.security.jwt.audience", () -> "antares-test-audience");
    registry.add("application.security.jwt.cookie.domain", () -> "antares-domain");
    registry.add("application.security.internal-secret", () -> "test-internal-secret");
    // Every test request comes from the same IP: registration throttling is tested on its own
    registry.add("application.security.registration.max-per-ip", () -> "1000");

    registry.add("management.tracing.sampling.probability", () -> "0");
    registry.add("management.otlp.metrics.export.enabled", () -> "false");
    registry.add("management.tracing.export.otlp.enabled", () -> "false");
    registry.add("management.logging.export.otlp.enabled", () -> "false");
  }

  @BeforeEach
  void mailSenderBuildsRealMessages() {
    Mockito.when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage((Session) null));
  }

  @AfterEach
  void cleanRedis() {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  /**
   * Registers an account and marks its address verified, as if the mailed link had been opened
   * (test accounts join gyms). Use {@link #registerUnverified} to keep it unverified.
   */
  protected void register(String email, String password) throws Exception {
    registerUnverified(email, password);
    User user = userRepository.findByEmail(email).orElseThrow();
    user.setEmailVerified(true);
    userRepository.save(user);
  }

  /** Registers an account through the API, leaving its address unverified. */
  protected void registerUnverified(String email, String password) throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("Test", "User", email, password);
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated());
  }

  protected Cookie[] login(String email, String password) throws Exception {
    AuthenticationRequest loginRequest = new AuthenticationRequest(email, password);
    MvcResult result =
        mockMvc
            .perform(
                post("/antares/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn();
    return result.getResponse().getCookies();
  }

  protected Cookie[] registerAndLogin(String email, String password) throws Exception {
    register(email, password);
    return login(email, password);
  }

  protected User createAdmin(String email, String password) {
    return userRepository.save(
        User.builder()
            .firstName("Admin")
            .lastName("User")
            .email(email)
            .password(passwordEncoder.encode(password))
            .platformRole(PlatformRole.ADMIN)
            .emailVerified(true)
            .build());
  }

  protected String getXsrfToken(Cookie[] cookies) {
    return Arrays.stream(cookies)
        .filter(c -> "XSRF-TOKEN".equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElseThrow(() -> new AssertionError("XSRF-TOKEN cookie not found"));
  }
}
