package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.BoardRecordResponse;
import apex.stellar.aldebaran.dto.CanonicalLiftResponse;
import apex.stellar.aldebaran.dto.MovementRecordsResponse;
import apex.stellar.aldebaran.dto.RecordPointResponse;
import apex.stellar.aldebaran.service.MovementRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-movement records (lot C): the athlete's own records, the canonical "N RM" lifts and the
 * records board of a box. Records are materialized from the scores (never written here).
 */
@RestController
@RequestMapping("/aldebaran/records")
@RequiredArgsConstructor
@Tag(name = "Records", description = "Per-movement records (RM) and boards")
public class RecordController {

  private final MovementRecordService recordService;

  /**
   * Retrieves the current user's records, grouped by movement and variant (base version first),
   * with the estimated 1RM of each variant (Epley, displayed only).
   *
   * @param movementId Optional movement (records of its page).
   * @return The records.
   */
  @GetMapping("/me")
  public ResponseEntity<List<MovementRecordsResponse>> getMyRecords(
      @RequestParam(required = false) Long movementId) {
    return ResponseEntity.ok(recordService.getMyRecords(movementId));
  }

  /**
   * Retrieves the progression of the current user's N RM on the base version of a movement: each
   * day a new best load was set, oldest first.
   *
   * @param movementId The movement.
   * @param reps N of the N RM (1 by default).
   * @param variantKey The variant (the base version by default).
   * @return The successive bests.
   */
  @GetMapping("/me/progress")
  public ResponseEntity<List<RecordPointResponse>> getMyProgress(
      @RequestParam Long movementId,
      @RequestParam(defaultValue = "1") int reps,
      @RequestParam(defaultValue = "") String variantKey) {
    return ResponseEntity.ok(recordService.getMyProgress(movementId, reps, variantKey));
  }

  /**
   * Lists the canonical lifts: public "N RM" tests of the catalog. Logging one sets the record; its
   * WOD leaderboard is the lift's leaderboard.
   *
   * @return The lifts, by movement name then N.
   */
  @GetMapping("/lifts")
  public ResponseEntity<List<CanonicalLiftResponse>> getCanonicalLifts() {
    return ResponseEntity.ok(recordService.getCanonicalLifts());
  }

  /**
   * Retrieves the records board of a box for a movement: the base-version records of its current
   * members, wherever they were set. Members of the box and admins only.
   *
   * @param gymId The box.
   * @param movementId The movement.
   * @param variantKey The variant (the base version by default).
   * @return The board lines (user IDs; names from Antares), heaviest first for each N.
   */
  @GetMapping("/gyms/{gymId}")
  @PreAuthorize("@securityService.isAdmin(principal) or principal.isMemberOf(#gymId)")
  public ResponseEntity<List<BoardRecordResponse>> getGymBoard(
      @PathVariable Long gymId,
      @RequestParam Long movementId,
      @RequestParam(defaultValue = "") String variantKey) {
    return ResponseEntity.ok(recordService.getGymBoard(gymId, movementId, variantKey));
  }
}
