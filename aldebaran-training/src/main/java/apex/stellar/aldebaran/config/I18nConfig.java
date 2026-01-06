package apex.stellar.aldebaran.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Configuration class for Internationalization (i18n).
 *
 * <p>This class defines a custom {@link LocaleResolver} bean used to determine the locale of
 * incoming requests based on the "X-Auth-User-Locale" HTTP header. If the header is not set or
 * contains an invalid locale, a fallback mechanism provided by the parent class is used.
 *
 * <p>The custom resolver is implemented by extending {@link AcceptHeaderLocaleResolver}.
 */
@Configuration
@Slf4j
public class I18nConfig {

  /**
   * Defines a custom {@link LocaleResolver} bean to determine the locale of incoming HTTP requests.
   * The custom implementation uses the "X-Auth-User-Locale" HTTP header to extract the desired
   * locale. If the header is absent or invalid, a fallback mechanism relying on the parent's
   * behavior is used.
   *
   * @return a {@link LocaleResolver} instance that resolves locales based on the
   *     "X-Auth-User-Locale" header or defaults to the parent's implementation if unavailable.
   */
  @Bean
  public LocaleResolver localeResolver() {
    return new ForwardedLocaleResolver();
  }

  static class ForwardedLocaleResolver extends AcceptHeaderLocaleResolver {
    /**
     * Resolves the locale from the "X-Auth-User-Locale" header, falling back to the Accept-Language
     * header.
     *
     * @param request The HTTP request.
     * @return The resolved Locale.
     */
    @Override
    @NonNull
    public Locale resolveLocale(@NonNull HttpServletRequest request) {
      String headerLocale = request.getHeader("X-Auth-User-Locale");
      if (headerLocale != null && !headerLocale.isBlank()) {
        try {
          return Locale.forLanguageTag(headerLocale);
        } catch (Exception e) {
          log.warn("Error while configuring locale: {}", e.getMessage());
        }
      }
      return super.resolveLocale(request);
    }
  }
}
