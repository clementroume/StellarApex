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
 * Configuration class for internationalization (i18n) support. Sets up message sources and the
 * custom locale resolver for the application.
 */
@Configuration
public class I18nConfig {

  /**
   * Configures the source for translation messages. Loads files named `messages_xx.properties` from
   * the classpath.
   *
   * @return the message source bean
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
   * Configures the custom LocaleResolver that determines the locale based on the authenticated
   * user's preferences or the 'Accept-Language' header.
   *
   * @return the locale resolver bean
   */
  @Bean
  public LocaleResolver localeResolver() {

    return new UserLocaleResolver();
  }

  /**
   * Custom LocaleResolver that first checks the authenticated user's locale preference. If the user
   * is not authenticated, it falls back to the 'Accept-Language' header.
   */
  private static class UserLocaleResolver extends AcceptHeaderLocaleResolver {

    /**
     * Resolves the locale for the current request. If a user is authenticated, it uses the locale
     * from the user's profile. Otherwise, it falls back to the default behavior of using the
     * 'Accept-Language' header.
     *
     * @param request the current HTTP request
     * @return the resolved Locale
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
     * This method is not supported because the locale is tied to user preferences and cannot be
     * changed via this method.
     *
     * @param request the current HTTP request
     * @param response the current HTTP response
     * @param locale the new locale to set (not used)
     * @throws UnsupportedOperationException always thrown to indicate this operation is not
     *     supported
     */
    @Override
    public void setLocale(
        @NonNull HttpServletRequest request, HttpServletResponse response, Locale locale) {

      throw new UnsupportedOperationException(
          "Cannot change locale via setLocale - use the user preferences endpoint instead.");
    }
  }
}
