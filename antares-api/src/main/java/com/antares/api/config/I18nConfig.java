package com.antares.api.config;

import com.antares.api.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Configuration class for internationalization (i18n) support.
 *
 * <p>Sets up the message source for translations and a custom {@link LocaleResolver} that
 * prioritizes the authenticated user's preferred locale.
 */
@Configuration
public class I18nConfig {

  /**
   * Configures the source for translation messages (e.g., messages_en.properties,
   * messages_fr.properties).
   *
   * @return The message source bean.
   */
  @Bean
  public MessageSource messageSource() {

    ReloadableResourceBundleMessageSource messageSource =
        new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:messages");
    messageSource.setDefaultEncoding("ISO-8859-1");

    return messageSource;
  }

  /**
   * Configures the custom {@link LocaleResolver} bean.
   *
   * @return The UserLocaleResolver instance.
   */
  @Bean
  public LocaleResolver localeResolver() {

    return new UserLocaleResolver();
  }

  /**
   * Custom LocaleResolver that prioritizes the authenticated user's preference.
   *
   * <p>If a user is authenticated, their 'locale' preference is used. If not, it falls back to the
   * 'Accept-Language' header.
   */
  private static class UserLocaleResolver extends AcceptHeaderLocaleResolver {

    /**
     * Resolves the locale for the current request.
     *
     * @param request The current HTTP request.
     * @return The resolved Locale.
     */
    @Override
    @NonNull
    public Locale resolveLocale(@NonNull HttpServletRequest request) {

      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null
          && authentication.isAuthenticated()
          && authentication.getPrincipal() instanceof User user) {
        try {
          return Locale.forLanguageTag(user.getLocale());
        } catch (Exception e) {
          return Locale.ENGLISH;
        }
      }

      return super.resolveLocale(request);
    }

    /**
     * This operation is unsupported. Locale changes must go through the user preferences API
     * endpoint, not via session attributes.
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
