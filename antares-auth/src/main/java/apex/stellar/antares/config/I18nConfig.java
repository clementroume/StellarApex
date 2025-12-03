package apex.stellar.antares.config;

import apex.stellar.antares.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Configuration class for Internationalization (i18n) support.
 *
 * <p>This configuration sets up the message source for translations and defines a custom {@link
 * LocaleResolver} strategy. This strategy prioritizes the authenticated user's preferred locale
 * (stored in the database) over the browser's {@code Accept-Language} header.
 */
@Configuration
public class I18nConfig {

  /**
   * Configures the custom {@link LocaleResolver} bean.
   *
   * @return The {@link UserLocaleResolver} instance used by Spring MVC to determine the current
   *     locale.
   */
  @Bean
  public LocaleResolver localeResolver() {
    return new UserLocaleResolver();
  }

  /**
   * Custom implementation of {@link LocaleResolver} that integrates with Spring Security.
   *
   * <p>Resolution Strategy:
   *
   * <ol>
   *   <li>If a user is authenticated, use the {@code locale} stored in their profile.
   *   <li>Otherwise, fall back to the standard {@code Accept-Language} HTTP header.
   * </ol>
   */
  private static class UserLocaleResolver extends AcceptHeaderLocaleResolver {

    /**
     * Resolves the locale for the current HTTP request.
     *
     * @param request The incoming HTTP request.
     * @return The resolved {@link Locale}.
     */
    @Override
    @NonNull
    public Locale resolveLocale(@NonNull HttpServletRequest request) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

      if (authentication != null
          && authentication.isAuthenticated()
          && authentication.getPrincipal() instanceof User user) {
        return Locale.forLanguageTag(user.getLocale());
      }

      return super.resolveLocale(request);
    }

    /**
     * Sets the current locale.
     *
     * <p>This operation is intentionally unsupported in this architecture. Locale changes must be
     * performed via the dedicated user preferences API endpoint ({@code PATCH
     * /users/me/preferences}), which persists the change to the database.
     *
     * @throws UnsupportedOperationException always.
     */
    @Override
    public void setLocale(
        @NonNull HttpServletRequest request, HttpServletResponse response, Locale locale) {
      throw new UnsupportedOperationException(
          "Cannot change locale via setLocale - use the user preferences endpoint instead.");
    }
  }
}
