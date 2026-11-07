package apex.stellar.antares.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import apex.stellar.antares.exception.DemoAccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests of the rule "a demo account is never a platform administrator". */
class UserDemoTest {

  private static User demo() {
    return User.builder().email("demo@x.test").platformRole(PlatformRole.USER).demo(true).build();
  }

  @Test
  @DisplayName("setPlatformRole(ADMIN) on a demo account is refused")
  void setPlatformRole_Admin_OnDemo_IsRefused() {
    User user = demo();

    assertThatThrownBy(() -> user.setPlatformRole(PlatformRole.ADMIN))
        .isInstanceOf(DemoAccountException.class);
    assertThat(user.getPlatformRole()).isEqualTo(PlatformRole.USER);
  }

  @Test
  @DisplayName("setDemo(true) on an administrator is refused")
  void setDemo_OnAdmin_IsRefused() {
    User admin = User.builder().email("a@x.test").platformRole(PlatformRole.ADMIN).build();

    assertThatThrownBy(() -> admin.setDemo(true)).isInstanceOf(DemoAccountException.class);
    assertThat(admin.isDemo()).isFalse();
  }

  @Test
  @DisplayName("the JPA lifecycle check refuses a demo administrator built with the builder")
  void lifecycleCheck_DemoAdmin_IsRefused() {
    User tampered =
        User.builder().email("t@x.test").platformRole(PlatformRole.ADMIN).demo(true).build();

    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(tampered, "checkDemoIsNotAdmin"))
        .isInstanceOf(DemoAccountException.class);
  }

  @Test
  @DisplayName("the effective role and the authorities of a demo account are USER whatever is stored")
  void effectiveRole_OfDemo_IsAlwaysUser() {
    User tampered =
        User.builder().email("t@x.test").platformRole(PlatformRole.ADMIN).demo(true).build();

    assertThat(tampered.getPlatformRole()).isEqualTo(PlatformRole.USER);
    assertThat(tampered.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
  }

  @Test
  @DisplayName("a regular administrator keeps ADMIN")
  void regularAdmin_KeepsAdmin() {
    User admin = User.builder().email("a@x.test").platformRole(PlatformRole.ADMIN).build();

    assertThat(admin.getPlatformRole()).isEqualTo(PlatformRole.ADMIN);
    assertThat(admin.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
  }
}
