package atlas.stellar.antares.config;

import atlas.stellar.antares.model.Role;
import atlas.stellar.antares.model.User;
import atlas.stellar.antares.repository.UserRepository;
import atlas.stellar.antares.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository;
import org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
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
@Slf4j
public class ApplicationConfig {

  private final UserRepository userRepository;
  private final MessageSource messageSource;

  /**
   * Configures the {@link UserDetailsService} bean.
   *
   * <p>This bean tells Spring Security how to load a user by their email address (which serves as
   * the 'username' in this application).
   *
   * @return The UserDetailsService implementation.
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return email ->
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () ->
                    new UsernameNotFoundException(
                        messageSource.getMessage(
                            "error.user.not.found.email",
                            new Object[] {email},
                            LocaleContextHolder.getLocale())));
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
   * @throws Exception if the manager cannot be retrieved.
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }

  /**
   * Initializes a default admin user if no admin user currently exists.
   *
   * <p>This {@link ApplicationRunner} bean runs on application startup. It checks if any user with
   * the {@code ROLE_ADMIN} exists and, if not, creates one using credentials from the application
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
  public ApplicationRunner adminUserInitializer(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${application.admin.default-firstname}") String firstName,
      @Value("${application.admin.default-lastname}") String lastName,
      @Value("${application.admin.default-email}") String adminEmail,
      @Value("${application.admin.default-password}") String adminPassword) {
    return args -> {
      if (!userRepository.existsByRole(Role.ROLE_ADMIN)) {
        User adminUser =
            User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ROLE_ADMIN)
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
