package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.ReadableTextsRequest;
import apex.stellar.aldebaran.dto.UncountedLineResponse;
import apex.stellar.aldebaran.dto.WeeklyBalanceResponse;
import apex.stellar.aldebaran.service.BalanceCalculator;
import apex.stellar.aldebaran.service.BalanceMaintenanceService;
import apex.stellar.aldebaran.service.WeeklyBalanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The weekly balance (lot D): the athlete's own muscles, volume, metcons, strength and
 * modalities, computed from their scores and journals.
 */
@RestController
@RequestMapping("/aldebaran/balance")
@RequiredArgsConstructor
@Tag(name = "Balance", description = "Weekly balance of the athlete")
public class BalanceController {

  private final WeeklyBalanceService balanceService;
  private final BalanceMaintenanceService maintenanceService;

  /**
   * Retrieves the current user's balance for a week: the sections over that week and the 3
   * before, the volume over that week and the 7 before. Stale weeks are rebuilt first.
   *
   * @param week Any day of the week (its Monday, the athlete's local day).
   * @return The balance.
   */
  @GetMapping("/me")
  public ResponseEntity<WeeklyBalanceResponse> getMyBalance(
      @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate week) {
    return ResponseEntity.ok(balanceService.getMyBalance(week));
  }

  /**
   * Marks every stored week to rebuild and recomputes the modalities of every WOD (rules or
   * catalog changed, calibration during the beta). Platform administrators only.
   *
   * @return 204.
   */
  @PostMapping("/recompute")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> recompute() {
    maintenanceService.recomputeAll();
    return ResponseEntity.noContent().build();
  }

  /**
   * Says, for each text of a WOD line being edited, whether the balance can read it (D121): the
   * WOD form warns under the field, never blocks. A blank text reads the line's measures: true.
   *
   * @param request The texts, in the order of the lines.
   * @return One flag per text.
   */
  @PostMapping("/readable-texts")
  public ResponseEntity<List<Boolean>> readableTexts(@Valid @RequestBody ReadableTextsRequest request) {
    return ResponseEntity.ok(request.texts().stream().map(BalanceCalculator::readableText).toList());
  }

  /**
   * Lists every WOD line that counts nothing in the balance: an unreadable text, or a unit without
   * the value of one set in the catalog (D121 rule 5, D126). Platform administrators only.
   *
   * @return The lines, by WOD title then position.
   */
  @GetMapping("/uncounted-lines")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UncountedLineResponse>> uncountedLines() {
    return ResponseEntity.ok(maintenanceService.uncountedLines());
  }
}
