package atlas.stellar.vega;

import static org.springframework.security.config.Customizer.withDefaults;

import de.codecentric.boot.admin.server.config.AdminServerProperties;
import jakarta.servlet.DispatcherType;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * A security configuration class for the Antares Admin application.
 *
 * <p>This class configures the security settings of the application, including authentication,
 * authorization, CSRF protection, and session management. It uses Spring Security to define the
 * security behavior for the application.
 */
@Configuration
public class SecurityConfig {

  private final AdminServerProperties adminServer;

  public SecurityConfig(AdminServerProperties adminServer) {
    this.adminServer = adminServer;
  }

  /**
   * Configures the security filter chain for the application using Spring Security.
   *
   * <p>This method sets up authentication and authorization rules, CSRF protection, session
   * management, and additional security settings. It integrates form-based login, basic
   * authentication, and custom success handlers.
   *
   * @param http the {@code HttpSecurity} object provided by Spring Security to configure the filter
   *     chain
   * @return the {@code SecurityFilterChain} object representing the configured security filter
   *     chain
   * @throws Exception if an error occurs during security configuration
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    SavedRequestAwareAuthenticationSuccessHandler successHandler =
        new SavedRequestAwareAuthenticationSuccessHandler();
    successHandler.setTargetUrlParameter("redirectTo");
    successHandler.setDefaultTargetUrl(this.adminServer.path("/"));

    http.authorizeHttpRequests(
            authorizeRequests ->
                authorizeRequests
                    .requestMatchers("/assets/**", "/actuator/info", "/actuator/health", "/login")
                    .permitAll()
                    .dispatcherTypeMatchers(DispatcherType.ASYNC)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            formLogin ->
                formLogin.loginPage(this.adminServer.path("/login")).successHandler(successHandler))
        .httpBasic(withDefaults())
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers("/instances", "/instances/**", "/actuator/**"))
        .rememberMe(
            rememberMe ->
                rememberMe.key(UUID.randomUUID().toString()).tokenValiditySeconds(1209600));

    return http.build();
  }

  /**
   * Creates and configures an in-memory user details manager for authentication.
   *
   * <p>This method sets up a user with credentials and roles defined in the application properties.
   * The user details manager can be used for authentication in a Spring Security context.
   *
   * @param adminEmail the default email address of the admin user, injected from application
   *     properties
   * @param adminPassword the default password of the admin user, injected from application
   *     properties
   * @return an instance of {@code InMemoryUserDetailsManager} configured with the admin user
   *     details
   */
  @Bean
  public InMemoryUserDetailsManager userDetailsService(
      @Value("${application.admin.default-email}") String adminEmail,
      @Value("${application.admin.default-password}") String adminPassword) {

    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    UserDetails user =
        User.builder()
            .username(adminEmail)
            .password(encoder.encode(adminPassword))
            .roles("USER")
            .build();

    return new InMemoryUserDetailsManager(user);
  }
}
