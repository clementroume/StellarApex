package com.antares.api.service;

import com.antares.api.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** CookieService handles adding and clearing HTTP cookies in the response. */
@Service
@RequiredArgsConstructor
public class CookieService {

  private final JwtProperties jwtProperties;

  /**
   * Adds a cookie with the specified name, value, and max age to the HTTP response.
   *
   * @param name the name of the cookie
   * @param value the value of the cookie
   * @param maxAgeMs the maximum age of the cookie in milliseconds
   * @param response the HTTP response to add the cookie to
   */
  public void addCookie(String name, String value, long maxAgeMs, HttpServletResponse response) {

    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(jwtProperties.cookie().secure())
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAgeMs / 1000)
            .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }

  /**
   * Clears the specified cookie by setting its max age to 0.
   *
   * @param name the name of the cookie to clear
   * @param response the HTTP response to add the cleared cookie to
   */
  public void clearCookie(String name, HttpServletResponse response) {

    ResponseCookie cookie =
        ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(jwtProperties.cookie().secure())
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }
}
