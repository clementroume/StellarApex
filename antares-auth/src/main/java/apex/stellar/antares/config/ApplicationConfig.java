package apex.stellar.antares.config;

import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.UserRepository;
import apex.stellar.antares.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main application configuration class.
 *
 * <p>This class defines core Spring beans required for security, data access, and application
 * initialization, such as the {@link UserDetailsService}, {@link PasswordEncoder}, and the default
 * admin user initializer.
 */
@Configuration
@RequiredArgsConstructor
@EnableJpaAuditing
@Slf4j
public class ApplicationConfig {

  private final UserRepository userRepository;
  private final MessageSource messageSource;

  /**
   * Configures the {@link UserDetailsService} bean with caching support.
   *
   * <p>This bean loads user details by email. It is annotated with {@link Cacheable} to store
   * results in Redis (cache name "users"), significantly reducing database hits during token
   * validation and API requests.
   *
   * @return The UserDetailsService implementation.
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return new UserDetailsService() {
      @Override
      @Cacheable(value = "users", key = "#email")
      @NonNull
      public UserDetails loadUserByUsername(@NonNull String email) {
        return userRepository
            .findByEmail(email)
            .orElseThrow(
                () ->
                    new UsernameNotFoundException(
                        messageSource.getMessage(
                            "error.user.not.found.email",
                            new Object[] {email},
                            LocaleContextHolder.getLocale())));
      }
    };
  }

  /**
   * Configures the {@link PasswordEncoder} bean.
   *
   * <p>This bean defines the hashing algorithm (BCrypt) to be used for storing and verifying user
   * passwords.
   *
   * @return A BCryptPasswordEncoder instance.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Exposes the {@link AuthenticationManager} bean.
   *
   * <p>This manager is the core of Spring Security's authentication mechanism and is used by the
   * {@link AuthenticationService} to process logins.
   *
   * @param config The autoconfigured AuthenticationConfiguration.
   * @return The AuthenticationManager.
   * @throws AuthenticationException if the manager cannot be retrieved.
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws AuthenticationException {
    return config.getAuthenticationManager();
  }

  /**
   * Initializes a default admin user if no admin user currently exists.
   *
   * <p>This {@link ApplicationRunner} bean runs on application startup. It checks if any user with
   * the {@code ADMIN} role exists and, if not, creates one using credentials from the application
   * properties.
   *
   * @param userRepository The repository to check for and save the user.
   * @param passwordEncoder The encoder to hash the default password.
   * @param firstName Default admin's first name from properties.
   * @param lastName Default admin's last name from properties.
   * @param adminEmail Default admin's email from properties.
   * @param adminPassword Default admin's plain-text password from properties.
   * @return The ApplicationRunner bean.
   */
  @Bean
  @Transactional
  @Order(1)
  public ApplicationRunner adminUserInitializer(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${application.admin.default-firstname}") String firstName,
      @Value("${application.admin.default-lastname}") String lastName,
      @Value("${application.admin.default-email}") String adminEmail,
      @Value("${application.admin.default-password}") String adminPassword) {
    return ignored -> {
      if (!userRepository.existsByPlatformRole(PlatformRole.ADMIN)) {
        User adminUser =
            User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .platformRole(PlatformRole.ADMIN)
                .locale("fr")
                .theme("dark")
                .build();
        userRepository.save(adminUser);
        log.info("Default admin user created with email: {}", adminEmail);
      }
    };
  }

  /**
   * Enables HTTP request tracing for the "/actuator/httpexchanges" endpoint.
   *
   * <p>This bean is used by Spring Boot Admin to display the "Traces" tab.
   *
   * @return An in-memory repository for HTTP exchanges.
   */
  @Bean
  public HttpExchangeRepository httpExchangeRepository() {
    return new InMemoryHttpExchangeRepository();
  }
}
