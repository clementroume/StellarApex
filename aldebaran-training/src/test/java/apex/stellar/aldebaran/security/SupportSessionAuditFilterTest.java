package apex.stellar.aldebaran.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit tests for {@link SupportSessionAuditFilter}. */
class SupportSessionAuditFilterTest {

  private final SupportSessionAuditFilter filter = new SupportSessionAuditFilter();
  private final Logger logger = (Logger) LoggerFactory.getLogger(SupportSessionAuditFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void cleanUp() {
    logger.detachAppender(appender);
    SecurityContextHolder.clearContext();
  }

  private static void authenticate(Long actorId) {
    AldebaranUserPrincipal principal =
        AldebaranUserPrincipal.builder()
            .id(42L)
            .platformRole("USER")
            .memberships(Map.of())
            .actorId(actorId)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }

  private MockHttpServletResponse run(String method) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/aldebaran/wods/7");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            ((MockHttpServletResponse) res).setStatus(204);
          }
        };
    filter.doFilter(request, response, chain);
    return response;
  }

  @Test
  @DisplayName("Write with an actor is logged at INFO with actor, user, method, path and status")
  void shouldLogWriteWithActor() throws Exception {
    authenticate(1L);

    MockHttpServletResponse response = run("DELETE");

    assertEquals(204, response.getStatus());
    assertEquals(1, appender.list.size());
    ILoggingEvent event = appender.list.getFirst();
    assertEquals(Level.INFO, event.getLevel());
    assertEquals(
        "Support session write: actorId=1, userId=42, method=DELETE, path=/aldebaran/wods/7,"
            + " status=204",
        event.getFormattedMessage());
  }

  @Test
  @DisplayName("Read with an actor is not logged")
  void shouldNotLogReadWithActor() throws Exception {
    authenticate(1L);

    run("GET");

    assertTrue(appender.list.isEmpty());
  }

  @Test
  @DisplayName("Write without an actor is not logged")
  void shouldNotLogWriteWithoutActor() throws Exception {
    authenticate(null);

    run("POST");

    assertTrue(appender.list.isEmpty());
  }

  @Test
  @DisplayName("Unauthenticated request passes through without log")
  void shouldIgnoreUnauthenticated() throws Exception {
    MockHttpServletResponse response = run("PUT");

    assertEquals(204, response.getStatus());
    assertTrue(appender.list.isEmpty());
  }
}
