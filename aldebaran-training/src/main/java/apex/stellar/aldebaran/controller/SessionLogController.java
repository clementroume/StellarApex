package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.SessionLogBlockRequest;
import apex.stellar.aldebaran.dto.SessionLogResponse;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.SessionLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST Controller of the athletes' session journals ("Séance faite", blocks done). */
@RestController
@RequestMapping("/aldebaran/session-logs")
@RequiredArgsConstructor
@Tag(name = "Session journal", description = "Sessions done by the athlete")
public class SessionLogController {

  private final SessionLogService sessionLogService;

  /**
   * Retrieves the journal of the current user, latest first.
   *
   * @param from Optional first day.
   * @param to Optional last day.
   * @param pageable Pagination (20 per page by default).
   * @param principal The authenticated user.
   * @return The journals with their scores.
   */
  @GetMapping("/me")
  public ResponseEntity<Slice<SessionLogResponse>> getMyLogs(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @PageableDefault(size = 20) Pageable pageable,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(sessionLogService.getMyLogs(from, to, pageable, principal));
  }

  /**
   * Checks or unchecks a block of a journal (its athlete only).
   *
   * @param id The journal.
   * @param blockId The block.
   * @param request The done flag.
   * @param principal The authenticated user.
   * @return The journal.
   */
  @PutMapping("/{id}/blocks/{blockId}")
  @PreAuthorize("@sessionSecurity.canManageLog(#id, principal)")
  public ResponseEntity<SessionLogResponse> setBlockDone(
      @PathVariable Long id,
      @PathVariable Long blockId,
      @RequestBody SessionLogBlockRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        sessionLogService.setBlockDone(id, blockId, request.done(), principal));
  }

  /**
   * Deletes a journal (409 while scores are linked to it).
   *
   * @param id The journal.
   * @return 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@sessionSecurity.canManageLog(#id, principal)")
  public ResponseEntity<Void> deleteLog(@PathVariable Long id) {

    sessionLogService.deleteLog(id);
    return ResponseEntity.noContent().build();
  }
}
