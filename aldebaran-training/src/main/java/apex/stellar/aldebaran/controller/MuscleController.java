package apex.stellar.aldebaran.controller;

import apex.stellar.aldebaran.dto.MuscleReferenceData;
import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.dto.MuscleResponse;
import apex.stellar.aldebaran.service.MuscleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** REST Controller for managing the Anatomical Muscle Catalog. */
@RestController
@RequestMapping("/aldebaran/muscles")
@RequiredArgsConstructor
@Tag(name = "Muscles", description = "Muscle reference data management")
public class MuscleController {

  private final MuscleService muscleService;

  /**
   * Retrieves the anatomical catalog.
   *
   * @return A list of {@link MuscleResponse} objects.
   */
  @GetMapping
  public ResponseEntity<List<MuscleResponse>> getMuscles() {

    return ResponseEntity.ok(muscleService.getAllMuscles());
  }

  /**
   * Retrieves details of a specific muscle by its ID.
   *
   * @param id The ID of the muscle.
   * @return The corresponding {@link MuscleResponse}.
   */
  @GetMapping("/{id}")
  public ResponseEntity<MuscleResponse> getMuscle(@PathVariable Long id) {

    return ResponseEntity.ok(muscleService.getMuscle(id));
  }

  /**
   * Creates a new muscle entry in the catalog.
   *
   * @param muscleRequest The muscle creation payload.
   * @return A {@link MuscleResponse} of the created muscle with HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<MuscleResponse> createMuscle(
      @Valid @RequestBody MuscleRequest muscleRequest) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(muscleService.createMuscle(muscleRequest));
  }

  /**
   * Updates an existing muscle definition.
   *
   * @param id The unique identifier of the muscle to update.
   * @param muscleRequest The updated data.
   * @return A {@link MuscleResponse} of the updated muscle.
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<MuscleResponse> updateMuscle(
      @PathVariable Long id, @Valid @RequestBody MuscleRequest muscleRequest) {

    return ResponseEntity.ok(muscleService.updateMuscle(id, muscleRequest));
  }

  /**
   * Delete an existing muscle definition.
   *
   * @param id The unique identifier of the muscle to update.
   * @return A HTTP 204 No Content.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteMuscle(@PathVariable Long id) {

    muscleService.deleteMuscle(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Retrieves reference data (muscleGroups and muscleRoles) for UI forms.
   *
   * @return The {@link MuscleReferenceData}.
   */
  @GetMapping("/reference-data")
  public ResponseEntity<MuscleReferenceData> getReferenceData() {

    return ResponseEntity.ok(muscleService.getReferenceData());
  }
}
