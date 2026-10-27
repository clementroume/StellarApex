package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.WodCapabilities;
import apex.stellar.aldebaran.dto.WodResponse;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.WodSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the per-user {@link WodCapabilities} attached to WOD detail responses.
 *
 * <p>Kept apart from {@link WodService#getWodDetail(Long)} on purpose: the detail is cached
 * globally, whereas capabilities depend on the requesting user and must be computed per request.
 */
@Service
@RequiredArgsConstructor
public class WodCapabilityService {

  private final WodSecurity wodSecurity;
  private final ScoreRepository scoreRepository;

  /**
   * Returns a copy of the WOD enriched with the current user's capabilities.
   *
   * @param wod The WOD detail.
   * @param principal The authenticated user.
   * @return The WOD detail with its capabilities.
   */
  @Transactional(readOnly = true)
  public WodResponse withCapabilities(WodResponse wod, AldebaranUserPrincipal principal) {
    boolean canModify = wodSecurity.canModify(wod, principal);
    boolean locked = scoreRepository.existsByWodId(wod.id());
    boolean canJudge = wodSecurity.canJudge(wod, principal);
    return wod.withCapabilities(new WodCapabilities(canModify, locked, canJudge));
  }
}
