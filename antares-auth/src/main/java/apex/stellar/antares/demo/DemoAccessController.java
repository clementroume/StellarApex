package apex.stellar.antares.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the sign-in page whether demo accounts are available and gives their shared password, so a
 * visitor can try the app in one click (roadmap D68). The password is public by design: demo
 * accounts hold no privilege and can never be administrators. Configuration-driven: without {@code
 * application.demo.password} the endpoint answers 404 and the sign-in page shows nothing.
 */
@RestController
@RequestMapping("/antares/auth/demo")
@Tag(name = "Demo", description = "Access to the demo accounts")
public class DemoAccessController {

  private final String password;

  /**
   * Creates the controller.
   *
   * @param password The shared demo password; empty when the demo is not offered.
   */
  public DemoAccessController(@Value("${application.demo.password:}") String password) {
    this.password = password;
  }

  /**
   * Returns the shared password of the demo accounts.
   *
   * @return 200 with the password, or 404 when no demo is offered.
   */
  @GetMapping
  @Operation(summary = "Demo access", description = "Shared password of the demo accounts, if any.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Demo accounts are available"),
    @ApiResponse(responseCode = "404", description = "No demo on this deployment")
  })
  public ResponseEntity<@NonNull DemoAccessResponse> demoAccess() {
    if (password == null || password.isBlank()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(new DemoAccessResponse(password));
  }
}
