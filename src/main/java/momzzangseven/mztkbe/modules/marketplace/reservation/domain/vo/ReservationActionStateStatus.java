package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** Lifecycle state for one marketplace reservation escrow action attempt. */
public enum ReservationActionStateStatus {
  PREPARING,
  INTENT_BOUND,
  PREPARATION_FAILED,
  CONFIRMED,
  TERMINATED,
  ROLLED_BACK,
  STALE;

  public boolean isActive() {
    return this == PREPARING || this == INTENT_BOUND;
  }
}
