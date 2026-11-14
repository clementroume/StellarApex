package apex.stellar.aldebaran.repository.projection;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleRegion;

/** Sets of one muscle summed over some weeks of the balance, with what the screen names it by. */
@SuppressWarnings("unused")
public interface BalanceMuscleSum {

  /** @return The muscle. */
  Long getMuscleId();

  /** @return Its code in the Sirius drawing and model, or null. */
  String getCode();

  /** @return Its region. */
  MuscleRegion getRegion();

  /** @return Its medical name. */
  String getMedicalName();

  /** @return Its English common name, or null. */
  String getCommonNameEn();

  /** @return Its French common name, or null. */
  String getCommonNameFr();

  /** @return Sets in movement. */
  Double getMovingSets();

  /** @return Sets in holding. */
  Double getHoldingSets();
}
