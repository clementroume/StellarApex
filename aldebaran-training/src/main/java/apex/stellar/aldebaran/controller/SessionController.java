package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.SessionLogRequest;
import apex.stellar.aldebaran.dto.SessionLogResponse;
import apex.stellar.aldebaran.dto.SessionReferenceData;
import apex.stellar.aldebaran.dto.SessionRequest;
import apex.stellar.aldebaran.dto.SessionResponse;
import apex.stellar.aldebaran.dto.SessionSummaryResponse;
import apex.stellar.aldebaran.dto.SessionVisibilityRequest;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.enums.SessionScope;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.service.SessionLogService;
import apex.stellar.aldebaran.service.SessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller of training sessions: an ordered container of blocks proposed on a day (WOD
 * session) or a week (specialty). Members see the frame of their gym's sessions; the content opens
 * per the session's visibility policy; staff with WOD_WRITE write them.
 */
@RestController
@RequestMapping("/aldebaran/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Daily sessions, blocks and visibility")
public class SessionController {

  private final SessionService sessionService;
  private final SessionLogService sessionLogService;

  /**
   * Retrieves the frames of the readable sessions, by date (week planning, Today page).
   *
   * @param scope ALL (default), MINE (personal sessions) or GYM.
   * @param gymId Optional gym filter for the GYM scope.
   * @param from Optional first day (a specialty covers its whole week).
   * @param to Optional last day.
   * @param type Optional session type.
   * @param pageable Pagination (50 per page by default).
   * @param principal The authenticated user.
   * @return The session frames, with the user's content visibility and participation.
   */
  @GetMapping
  public ResponseEntity<Slice<SessionSummaryResponse>> getSessions(
      @RequestParam(defaultValue = "ALL") SessionScope scope,
      @RequestParam(required = false) Long gymId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) SessionType type,
      @PageableDefault(size = 50) Pageable pageable,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(
        sessionService.getSessions(scope, gymId, from, to, type, pageable, principal));
  }

  /**
   * Retrieves the reference values of the session forms.
   *
   * @return Types, disciplines, block kinds, stimuli and visibility policies.
   */
  @GetMapping("/reference-data")
  public ResponseEntity<SessionReferenceData> getReferenceData() {

    return ResponseEntity.ok(sessionService.getReferenceData());
  }

  /**
   * Retrieves a session: its frame, and its content when the visibility policy shows it.
   *
   * @param id The session.
   * @param principal The authenticated user.
   * @return The session with the user's capabilities and journal.
   */
  @GetMapping("/{id}")
  @PreAuthorize("@sessionSecurity.canRead(#id, principal)")
  public ResponseEntity<SessionResponse> getSession(
      @PathVariable Long id, @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(sessionService.getSession(id, principal));
  }

  /**
   * Creates a session (WOD_WRITE in its gym; anyone for a personal session).
   *
   * @param request The session and its blocks.
   * @param principal The authenticated user.
   * @return The created session.
   */
  @PostMapping
  @PreAuthorize("@sessionSecurity.canCreate(#request, principal)")
  public ResponseEntity<SessionResponse> createSession(
      @Valid @RequestBody SessionRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(sessionService.createSession(request, principal));
  }

  /**
   * Replaces a session and its blocks (409 once someone logged it).
   *
   * @param id The session.
   * @param request The new definition.
   * @param principal The authenticated user.
   * @return The updated session.
   */
  @PutMapping("/{id}")
  @PreAuthorize("@sessionSecurity.canModify(#id, principal)")
  public ResponseEntity<SessionResponse> updateSession(
      @PathVariable Long id,
      @Valid @RequestBody SessionRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(sessionService.updateSession(id, request, principal));
  }

  /**
   * Changes the visibility of a gym session, even once it is locked.
   *
   * @param id The session.
   * @param request The policy and local opening instant.
   * @param principal The authenticated user.
   * @return The updated session.
   */
  @PutMapping("/{id}/visibility")
  @PreAuthorize("@sessionSecurity.canModify(#id, principal)")
  public ResponseEntity<SessionResponse> updateVisibility(
      @PathVariable Long id,
      @Valid @RequestBody SessionVisibilityRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.ok(sessionService.updateVisibility(id, request, principal));
  }

  /**
   * Deletes a session and the WODs of its blocks (409 once someone logged it).
   *
   * @param id The session.
   * @return 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("@sessionSecurity.canModify(#id, principal)")
  public ResponseEntity<Void> deleteSession(@PathVariable Long id) {

    sessionService.deleteSession(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * "Séance faite": logs the session for the current user, every block checked.
   *
   * @param id The session.
   * @param request Optional day (today by default) and gym (personal session).
   * @param principal The authenticated user.
   * @return The journal.
   */
  @PostMapping("/{id}/logs")
  @PreAuthorize("@sessionSecurity.canRead(#id, principal)")
  public ResponseEntity<SessionLogResponse> logSession(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) SessionLogRequest request,
      @AuthenticationPrincipal AldebaranUserPrincipal principal) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(sessionLogService.logSession(id, request, principal));
  }
}
