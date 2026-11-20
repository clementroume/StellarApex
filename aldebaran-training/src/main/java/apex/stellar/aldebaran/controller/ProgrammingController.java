package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.OrderApplyRequest;
import apex.stellar.aldebaran.dto.OrderProposalRequest;
import apex.stellar.aldebaran.dto.OrderProposalResponse;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.ProgrammingBalanceService;
import apex.stellar.aldebaran.service.SessionOrderService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final SessionOrderService sessionOrderService;

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

  /**
   * Proposes an order of the cycle's sessions (D168-D171): the parts that move within each week
   * (WOD only, whole sessions, or WOD and Skills), never a locked day, a pinned block or a
   * competition; nothing is written. WOD_WRITE on the gym, or a platform admin.
   *
   * @param gymId The gym.
   * @param request The cycle, the switches, the mode, the strength-day option and the pins.
   * @return The proposal; 400 for a cycle out of bounds, 403 without WOD_WRITE.
   */
  @PostMapping("/gyms/{gymId}/order-proposals")
  @PreAuthorize("@sessionSecurity.canReorder(#gymId, principal)")
  public ResponseEntity<OrderProposalResponse> proposeOrder(
      @PathVariable Long gymId, @Valid @RequestBody OrderProposalRequest request) {
    return ResponseEntity.ok(sessionOrderService.propose(gymId, request));
  }

  /**
   * Applies the weeks of a proposal the coach keeps, in one transaction (D170). WOD_WRITE on the
   * gym, or a platform admin.
   *
   * @param gymId The gym.
   * @param request The weeks kept.
   * @return 204; 400 when a week no longer matches its sessions, 409 when a session became locked.
   */
  @PostMapping("/gyms/{gymId}/order")
  @PreAuthorize("@sessionSecurity.canReorder(#gymId, principal)")
  public ResponseEntity<Void> applyOrder(
      @PathVariable Long gymId, @Valid @RequestBody OrderApplyRequest request) {
    sessionOrderService.apply(gymId, request);
    return ResponseEntity.noContent().build();
  }
}
