package com.antares.api.config;

import com.antares.api.model.Role;
import com.antares.api.model.User;
import com.antares.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
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
 * ApplicationConfig class for configuring user details service, password encoder, authentication
 * manager, and initializing a default admin user.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApplicationConfig {

  private final UserRepository userRepository;
  private final MessageSource messageSource;

  /**
   * Configures the UserDetailsService to load user details by email.
   *
   * @return UserDetailsService instance
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
   * Configures the PasswordEncoder to use BCrypt hashing algorithm.
   *
   * @return PasswordEncoder instance
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Configures the AuthenticationManager using the provided AuthenticationConfiguration.
   *
   * @param config AuthenticationConfiguration instance
   * @return AuthenticationManager instance
   * @throws Exception if an error occurs while retrieving the AuthenticationManager
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {

    return config.getAuthenticationManager();
  }

  /**
   * Initializes a default admin user if none exists in the database.
   *
   * @param userRepository UserRepository instance
   * @param passwordEncoder PasswordEncoder instance
   * @param firstName Default first name for the admin user
   * @param lastName Default last name for the admin user
   * @param adminEmail Default email for the admin user
   * @param adminPassword Default password for the admin user
   * @return ApplicationRunner instance to run the initialization logic
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
}
