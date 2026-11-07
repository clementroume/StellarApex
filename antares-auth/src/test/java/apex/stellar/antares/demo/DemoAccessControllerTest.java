package apex.stellar.antares.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Unit tests of {@link DemoAccessController}. */
class DemoAccessControllerTest {

  @Test
  @DisplayName("demo access: the configured password is returned")
  void configuredPassword_IsReturned() {
    var response = new DemoAccessController("shared-demo-pw").demoAccess();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().password()).isEqualTo("shared-demo-pw");
  }

  @Test
  @DisplayName("demo access: 404 when no password is configured")
  void emptyPassword_IsNotFound() {
    assertThat(new DemoAccessController("").demoAccess().getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(new DemoAccessController("  ").demoAccess().getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
