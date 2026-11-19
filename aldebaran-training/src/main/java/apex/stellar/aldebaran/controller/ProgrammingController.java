package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.ProgrammingBalanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The balance of a gym's programming, for its staff (lot D'). */
@RestController
@RequestMapping("/aldebaran/programming")
@RequiredArgsConstructor
@Tag(name = "Programming", description = "Balance of a gym's programming, for its staff")
public class ProgrammingController {

  private final ProgrammingBalanceService balanceService;

  /**
   * Retrieves the balance of a gym's CrossFit sessions over a cycle: the full week, each attendance
   * profile of 3, 4 and 5 days, the coach's alerts (D161-D167). Staff of any role of the gym, or a
   * platform admin.
   *
   * @param gymId The gym.
   * @param start Any day of the first week of the cycle (its Monday).
   * @param weeks Weeks of the cycle, 4 to 8.
   * @param scaled True to read the official Scaled versions.
   * @param optional True to count the optional blocks.
   * @param days A profile to detail (days of the week), optional.
   * @param principal The authenticated user.
   * @return The balance; 400 for a cycle out of bounds, 403 for a non-staff user.
   */
  @GetMapping("/gyms/{gymId}/balance")
  @PreAuthorize("@sessionSecurity.canReadProgramming(#gymId, principal)")
  public ResponseEntity<ProgrammingBalanceResponse> getBalance(
      @PathVariable Long gymId,
      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate start,
      @RequestParam(defaultValue = "4") int weeks,
      @RequestParam(defaultValue = "false") boolean scaled,
      @RequestParam(defaultValue = "false") boolean optional,
      @RequestParam(required = false) Set<DayOfWeek> days,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {
    return ResponseEntity.ok(
        balanceService.getBalance(gymId, start, weeks, scaled, optional, days, principal));
  }
}
