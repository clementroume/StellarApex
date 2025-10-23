package com.antares.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antares.api.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@link CookieService}.
 *
 * <p>Each test follows the Given/When/Then pattern for clarity and maintainability.
 *
 * <p>Given: The initial state or preconditions for the test. When: The action or event being
 * tested. Then: The expected outcome or assertion.
 */
@ExtendWith(MockitoExtension.class)
class CookieServiceTest {

  @Mock private HttpServletResponse httpServletResponse;

  private CookieService cookieService;

  private void initializeService(boolean isSecure) {
    // Given: A JwtProperties instance with the desired secure flag.
    JwtProperties jwtProperties =
        new JwtProperties(
            "secret",
            "test-issuer",
            "test-audience",
            new JwtProperties.AccessToken(1L, "access"),
            new JwtProperties.RefreshToken(1L, "refresh"),
            new JwtProperties.CookieProperties(isSecure));
    cookieService = new CookieService(jwtProperties);
  }

  @Test
  @DisplayName("addCookie should create a secure, HttpOnly cookie when secure property is true")
  void testAddCookie_whenSecure_shouldBeSecureAndHttpOnly() {
    // Given: CookieService initialized with secure cookies.
    initializeService(true);
    String cookieName = "my-cookie";
    String cookieValue = "my-value";
    long maxAgeMs = 3600000; // 1 hour

    // When: Adding a cookie.
    cookieService.addCookie(cookieName, cookieValue, maxAgeMs, httpServletResponse);

    // Then: The Set-Cookie header contains Secure, HttpOnly, and correct attributes.
    ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpServletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

    String cookieHeader = headerCaptor.getValue();
    assertTrue(cookieHeader.contains("my-cookie=my-value"));
    assertTrue(cookieHeader.contains("Max-Age=3600"));
    assertTrue(cookieHeader.contains("HttpOnly"));
    assertTrue(cookieHeader.contains("Secure"));
    assertTrue(cookieHeader.contains("SameSite=Lax"));
    assertTrue(cookieHeader.contains("Path=/"));
  }

  @Test
  @DisplayName(
      "addCookie should create a non-secure, HttpOnly cookie when secure property is false")
  void testAddCookie_whenNotSecure_shouldBeHttpOnlyAndNotSecure() {
    // Given: CookieService initialized with non-secure cookies.
    initializeService(false);
    String cookieName = "my-cookie";
    String cookieValue = "my-value";
    long maxAgeMs = 3600000; // 1 hour

    // When: Adding a cookie.
    cookieService.addCookie(cookieName, cookieValue, maxAgeMs, httpServletResponse);

    // Then: The Set-Cookie header does not contain Secure, but contains HttpOnly.
    ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpServletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

    String cookieHeader = headerCaptor.getValue();
    assertTrue(cookieHeader.contains("my-cookie=my-value"));
    assertTrue(cookieHeader.contains("HttpOnly"));
    assertFalse(cookieHeader.contains("Secure"));
  }

  @Test
  @DisplayName("clearCookie should create a cookie with Max-Age=0")
  void testClearCookie_shouldSetMaxAgeToZero() {
    // Given: CookieService initialized (secure flag irrelevant for clearing).
    initializeService(true);
    String cookieName = "cookie-to-clear";

    // When: Clearing a cookie.
    cookieService.clearCookie(cookieName, httpServletResponse);

    // Then: The Set-Cookie header contains Max-Age=0 and HttpOnly.
    ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpServletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

    String cookieHeader = headerCaptor.getValue();
    assertTrue(cookieHeader.contains("cookie-to-clear="));
    assertTrue(cookieHeader.contains("Max-Age=0"));
    assertTrue(cookieHeader.contains("HttpOnly"));
  }
}
