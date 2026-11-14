package apex.stellar.aldebaran.repository.projection;

/** Strength sets of one lift summed over some weeks of the balance. */
@SuppressWarnings("unused")
public interface BalanceLiftSum {

  /** @return Name of the lift. */
  String getLabel();

  /** @return Its base movement, null for a complex. */
  Long getMovementId();

  /** @return Its sets. */
  Double getSets();

  /** @return 1 when one set was near the record, else 0. */
  Integer getNearRecord();
}
