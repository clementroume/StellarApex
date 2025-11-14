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

/** Unit tests for the {@link CookieService}. */
@ExtendWith(MockitoExtension.class)
class CookieServiceTest {

  @Mock private HttpServletResponse httpServletResponse;

  private CookieService cookieService;

  // Helper to initialize service with a specific security setting
  private void initializeService(boolean isSecure) {
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
  @DisplayName("addCookie: should create a secure, HttpOnly cookie when secure=true")
  void testAddCookie_whenSecure_shouldBeSecureAndHttpOnly() {
    // Given
    initializeService(true);
    long maxAgeMs = 3600000; // 1 hour

    // When
    cookieService.addCookie("my-cookie", "my-value", maxAgeMs, httpServletResponse);

    // Then
    ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpServletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

    String cookieHeader = headerCaptor.getValue();
    assertTrue(cookieHeader.contains("my-cookie=my-value"));
    assertTrue(cookieHeader.contains("Max-Age=3600"));
    assertTrue(cookieHeader.contains("HttpOnly"));
    assertTrue(cookieHeader.contains("Secure")); // Secure flag is present
    assertTrue(cookieHeader.contains("SameSite=Lax"));
    assertTrue(cookieHeader.contains("Path=/"));
  }

  @Test
  @DisplayName("addCookie: should create a non-secure, HttpOnly cookie when secure=false")
  void testAddCookie_whenNotSecure_shouldBeHttpOnlyAndNotSecure() {
    // Given
    initializeService(false); // Secure flag is false
    long maxAgeMs = 3600000; // 1 hour

    // When
    cookieService.addCookie("my-cookie", "my-value", maxAgeMs, httpServletResponse);

    // Then
    ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpServletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

    String cookieHeader = headerCaptor.getValue();
    assertTrue(cookieHeader.contains("my-cookie=my-value"));
    assertTrue(cookieHeader.contains("HttpOnly"));
    assertFalse(cookieHeader.contains("Secure")); // Secure flag is absent
  }

  @Test
  @DisplayName("clearCookie: should create a cookie with Max-Age=0")
  void testClearCookie_shouldSetMaxAgeToZero() {
    // Given
    initializeService(true);

    // When
    cookieService.clearCookie("cookie-to-clear", httpServletResponse);

    // Then
    ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
    verify(httpServletResponse).addHeader(eq("Set-Cookie"), headerCaptor.capture());

    String cookieHeader = headerCaptor.getValue();
    assertTrue(cookieHeader.contains("cookie-to-clear="));
    assertTrue(cookieHeader.contains("Max-Age=0"));
    assertTrue(cookieHeader.contains("HttpOnly"));
  }
}
