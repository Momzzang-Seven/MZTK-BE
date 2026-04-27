package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/**
 * Value Object representing the lifecycle state of a class reservation.
 *
 * <p>State-transition rules are centralised here so that service-layer code never compares status
 * strings directly. All status transitions must go through {@link
 * #canTransitionTo(ReservationStatus)}.
 */
public enum ReservationStatus {

  /** User reservation complete; awaiting trainer approval. Funds deposited in escrow. */
  PENDING,

  /** Trainer approved the reservation. No on-chain change; funds remain in escrow. */
  APPROVED,

  /** User voluntarily cancelled while PENDING. Server calls cancelClass → escrow refund. */
  USER_CANCELLED,

  /** Trainer explicitly rejected the PENDING reservation. Server calls cancelClass → refund. */
  REJECTED,

  /** Trainer failed to respond within 72 h OR 1 h before session start. Scheduler adminRefund. */
  TIMEOUT_CANCELLED,

  /** User confirmed class completion. Server calls confirmClass → trainer paid. */
  SETTLED,

  /** User did not complete within 24 h after class end. Scheduler adminSettle → trainer paid. */
  AUTO_SETTLED;

  /** Returns true if this status is a terminal state with no further transitions. */
  public boolean isTerminal() {
    return switch (this) {
      case USER_CANCELLED, REJECTED, TIMEOUT_CANCELLED, SETTLED, AUTO_SETTLED -> true;
      default -> false;
    };
  }

  /** Returns true when a manual cancellation is still permitted (PENDING only). */
  public boolean isCancellable() {
    return this == PENDING;
  }

  /**
   * Returns whether a transition from this status to {@code next} is permitted by business rules.
   *
   * @param next the target status
   * @return true if the transition is allowed
   */
  public boolean canTransitionTo(ReservationStatus next) {
    return switch (this) {
      case PENDING ->
          next == APPROVED
              || next == USER_CANCELLED
              || next == REJECTED
              || next == TIMEOUT_CANCELLED;
      case APPROVED -> next == SETTLED || next == AUTO_SETTLED;
      default -> false;
    };
  }
}
