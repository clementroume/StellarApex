package apex.stellar.antares.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Uses {@link MockMvc} in standalone mode to verify exception handling logic and JSON response
 * formatting without loading the full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  @Mock private MessageSource messageSource;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource);
    // Set up MockMvc with the handler and a dummy controller to trigger exceptions
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(handler).build();
  }

  @Test
  @DisplayName("handleNotFound: Should return 404 with localized message")
  void testHandleNotFound() throws Exception {
    when(messageSource.getMessage(eq("error.user.not.found"), any(), any(Locale.class)))
        .thenReturn("User not found");

    mockMvc
        .perform(get("/test/not-found").locale(Locale.ENGLISH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("User not found"))
        .andExpect(jsonPath("$.instance").value("/test/not-found"));
  }

  @Test
  @DisplayName("handleConflict: Should return 409 with localized message")
  void testHandleConflict() throws Exception {
    when(messageSource.getMessage(eq("error.email.exists"), any(), any(Locale.class)))
        .thenReturn("Email already exists");

    mockMvc
        .perform(get("/test/conflict").locale(Locale.ENGLISH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Data Conflict"))
        .andExpect(jsonPath("$.detail").value("Email already exists"));
  }

  @Test
  @DisplayName("handleBadCredentials: Should return 401 with generic message")
  void testHandleBadCredentials() throws Exception {
    when(messageSource.getMessage(eq("error.credentials.bad"), any(), any(Locale.class)))
        .thenReturn("Bad credentials");

    mockMvc
        .perform(get("/test/bad-credentials"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("Bad Credentials"))
        .andExpect(jsonPath("$.detail").value("Bad credentials"));
  }

  @Test
  @DisplayName("handleAccessDenied: Should return 403 with default message if key is missing")
  void testHandleAccessDenied_Generic() throws Exception {
    // Simulates a generic AccessDeniedException from Spring Security (no "error." prefix)
    when(messageSource.getMessage(eq("error.access.denied"), any(), any(Locale.class)))
        .thenReturn("Access Denied Default");

    mockMvc
        .perform(get("/test/access-denied-generic"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Access Denied Default"));
  }

  @Test
  @DisplayName("handleAccessDenied: Should return 403 with translated message if key exists")
  void testHandleAccessDenied_WithKey() throws Exception {
    // Simulates a custom AccessDeniedException thrown with a specific key
    when(messageSource.getMessage(eq("error.access.denied"), any(), any(Locale.class)))
        .thenReturn("Default"); // Fallback
    when(messageSource.getMessage(eq("error.specific"), any(), eq("Default"), any(Locale.class)))
        .thenReturn("Specific Translation");

    mockMvc
        .perform(get("/test/access-denied-key"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Specific Translation"));
  }

  @Test
  @DisplayName("handleAccountLocked: Should return 429 with localized message")
  void testHandleAccountLocked() throws Exception {
    when(messageSource.getMessage(eq("error.account.locked"), any(), any(Locale.class)))
        .thenReturn("Account is locked");

    mockMvc
        .perform(get("/test/locked"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.title").value("Account Locked"))
        .andExpect(jsonPath("$.detail").value("Account is locked"));
  }

  @Test
  @DisplayName("handleValidation: Should return 400 and aggregate field errors")
  void testHandleValidation() throws Exception {
    when(messageSource.getMessage(eq("error.validation"), any(), any(Locale.class)))
        .thenReturn("Validation Failed");

    // Sending empty JSON to trigger @NotNull validation
    mockMvc
        .perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Failed"))
        // Verify the detail contains the specific field error
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.containsString("field: must not be null")));
  }

  @Test
  @DisplayName("handleGeneric: Should return 500 for unexpected exceptions")
  void testHandleGeneric() throws Exception {
    when(messageSource.getMessage(eq("error.internal.server"), any(), any(Locale.class)))
        .thenReturn("Internal error");

    mockMvc
        .perform(get("/test/generic"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(jsonPath("$.detail").value("Internal error"));
  }

  // --- Dummy Controller & DTO for Testing ---

  @RestController
  static class TestController {

    @GetMapping("/test/not-found")
    void notFound() {
      throw new ResourceNotFoundException("error.user.not.found", 1L);
    }

    @GetMapping("/test/conflict")
    void conflict() {
      throw new DataConflictException("error.email.exists", "email@test.com");
    }

    @GetMapping("/test/bad-credentials")
    void badCreds() {
      throw new BadCredentialsException("Bad creds");
    }

    @GetMapping("/test/access-denied-generic")
    void accessDeniedGeneric() {
      throw new AccessDeniedException("Spring Security Default Message");
    }

    @GetMapping("/test/access-denied-key")
    void accessDeniedKey() {
      throw new AccessDeniedException("error.specific");
    }

    @GetMapping("/test/locked")
    void locked() {
      throw new AccountLockedException("error.account.locked", 30L);
    }

    @PostMapping("/test/validation")
    void validation(@RequestBody @Valid DummyDto dto) {
      // triggers MethodArgumentNotValidException if invalid
    }

    @GetMapping("/test/generic")
    void generic() {
      throw new RuntimeException("Unexpected boom");
    }
  }

  static class DummyDto {
    @NotNull(message = "must not be null")
    public String field;
  }
}
