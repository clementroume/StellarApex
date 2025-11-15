package atlas.stellar.antares.service;

import atlas.stellar.antares.config.JwtProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/** Service to handle adding and clearing secure, HttpOnly cookies. */
@Service
@RequiredArgsConstructor
public class CookieService {

  private final JwtProperties jwtProperties;

  /**
   * Adds an HttpOnly cookie to the HTTP response.
   *
   * @param name The name of the cookie.
   * @param value The value of the cookie.
   * @param maxAgeMs The maximum age in milliseconds.
   * @param response The HTTP response.
   */
  public void addCookie(String name, String value, long maxAgeMs, HttpServletResponse response) {

    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(jwtProperties.cookie().secure()) // From application.properties
            .sameSite("Lax") // Protects against most CSRF attacks
            .path("/")
            .maxAge(maxAgeMs / 1000) // Converts ms to seconds for Max-Age
            .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }

  /**
   * Clears an HttpOnly cookie by setting its max age to 0.
   *
   * @param name The name of the cookie to clear.
   * @param response The HTTP response.
   */
  public void clearCookie(String name, HttpServletResponse response) {

    ResponseCookie cookie =
        ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(jwtProperties.cookie().secure())
            .sameSite("Lax")
            .path("/")
            .maxAge(0) // Expires the cookie immediately
            .build();

    response.addHeader("Set-Cookie", cookie.toString());
  }
}
