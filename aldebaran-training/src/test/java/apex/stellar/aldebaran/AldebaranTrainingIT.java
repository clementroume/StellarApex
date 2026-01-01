package apex.stellar.aldebaran;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.controller.MuscleController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Integration test class to verify the Spring application context loading. */
@SpringBootTest
class AldebaranTrainingIT extends BaseIntegrationTest {

  @Autowired private MuscleController muscleController;

  /** Verifies that the Spring application context loads successfully. */
  @Test
  @DisplayName("Application context should load successfully")
  void contextLoads() {
    assertThat(muscleController).isNotNull();
  }
}
