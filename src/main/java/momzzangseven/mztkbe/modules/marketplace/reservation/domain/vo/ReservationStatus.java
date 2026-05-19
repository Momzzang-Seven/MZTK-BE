package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/**
 * Value Object representing the lifecycle state of a class reservation.
 *
 * <p>State-transition rules are centralised here so that service-layer code never compares status
 * strings directly. All status transitions must go through {@link
 * #canTransitionTo(ReservationStatus)}.
 */
public enum ReservationStatus {

  /** Local buyer/slot/date hold before purchase escrow is confirmed. */
  HOLDING,

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
  AUTO_SETTLED,

  /** Local purchase state before an execution intent is bound. Scheduler-invisible. */
  PURCHASE_PREPARING,

  /** User purchase intent exists or is awaiting signature/on-chain result. Scheduler-invisible. */
  PURCHASE_PENDING,

  /** Buyer cancellation is awaiting signature/on-chain result. Scheduler-invisible. */
  CANCEL_PENDING,

  /** Trainer rejection is awaiting signature/on-chain result. Scheduler-invisible. */
  REJECT_PENDING,

  /** Buyer completion confirmation is awaiting signature/on-chain result. Scheduler-invisible. */
  CONFIRM_PENDING,

  /** Buyer deadline refund is awaiting signature/on-chain result. Scheduler-invisible. */
  DEADLINE_REFUND_PENDING,

  /** Purchase confirmed but the actual contract deadline cannot safely support completion. */
  DEADLINE_RECOVERY_REQUIRED,

  /** Existing escrow row needs chain deadline/order sync before user actions are allowed. */
  DEADLINE_SYNC_REQUIRED,

  /** Deadline expired and buyer deadline refund is the only user-managed recovery path. */
  DEADLINE_REFUND_AVAILABLE,

  /** Chain state cannot be safely mapped without manual repair. */
  MANUAL_SYNC_REQUIRED,

  /** Local purchase hold expired before becoming a non-cancelable chain execution. */
  HOLD_EXPIRED,

  /** Purchase/payment preparation failed and no active hold remains. */
  PAYMENT_FAILED,

  /** Buyer deadline refund completed on-chain. */
  DEADLINE_REFUNDED;

  /** Returns true if this status is a terminal state with no further transitions. */
  public boolean isTerminal() {
    return switch (this) {
      case USER_CANCELLED,
              REJECTED,
              TIMEOUT_CANCELLED,
              SETTLED,
              AUTO_SETTLED,
              HOLD_EXPIRED,
              PAYMENT_FAILED,
              DEADLINE_REFUNDED ->
          true;
      default -> false;
    };
  }

  /** Returns true when a manual cancellation is still permitted (PENDING only). */
  public boolean isCancellable() {
    return this == PENDING;
  }

  /**
   * Returns true when this reservation is eligible for a timeout-cancel (PENDING only).
   *
   * <p>Used by {@code AutoCancelBatchItemProcessor} to re-validate status after acquiring a
   * pessimistic write lock, preventing double-compensation when a concurrent USER_CANCELLED has
   * already been committed.
   */
  public boolean canTimeoutCancel() {
    return this == PENDING;
  }

  /** Returns true when this status must not be processed by legacy scheduler/admin jobs. */
  public boolean isSchedulerInvisibleUserState() {
    return switch (this) {
      case HOLDING,
              PURCHASE_PREPARING,
              PURCHASE_PENDING,
              CANCEL_PENDING,
              REJECT_PENDING,
              CONFIRM_PENDING,
              DEADLINE_REFUND_PENDING,
              DEADLINE_RECOVERY_REQUIRED,
              DEADLINE_SYNC_REQUIRED,
              DEADLINE_REFUND_AVAILABLE,
              MANUAL_SYNC_REQUIRED,
              HOLD_EXPIRED,
              PAYMENT_FAILED,
              DEADLINE_REFUNDED ->
          true;
      default -> false;
    };
  }

  /** Returns true when this status should occupy class capacity. */
  public boolean countsTowardCapacity() {
    return !isTerminal();
  }

  /**
   * Returns whether a transition from this status to {@code next} is permitted by business rules.
   *
   * @param next the target status
   * @return true if the transition is allowed
   */
  public boolean canTransitionTo(ReservationStatus next) {
    return switch (this) {
      case HOLDING ->
          next == PENDING
              || next == HOLD_EXPIRED
              || next == PAYMENT_FAILED
              || next == DEADLINE_RECOVERY_REQUIRED
              || next == DEADLINE_SYNC_REQUIRED;
      case PENDING ->
          next == APPROVED
              || next == USER_CANCELLED
              || next == REJECTED
              || next == TIMEOUT_CANCELLED
              || next == HOLDING
              || next == PURCHASE_PREPARING
              || next == CANCEL_PENDING
              || next == REJECT_PENDING
              || next == DEADLINE_REFUND_AVAILABLE;
      case APPROVED ->
          next == SETTLED
              || next == AUTO_SETTLED
              || next == CONFIRM_PENDING
              || next == DEADLINE_REFUND_AVAILABLE;
      case PURCHASE_PREPARING ->
          next == PURCHASE_PENDING
              || next == PENDING
              || next == DEADLINE_RECOVERY_REQUIRED
              || next == DEADLINE_SYNC_REQUIRED
              || next == HOLD_EXPIRED
              || next == PAYMENT_FAILED;
      case PURCHASE_PENDING ->
          next == PENDING
              || next == HOLD_EXPIRED
              || next == PAYMENT_FAILED
              || next == DEADLINE_RECOVERY_REQUIRED
              || next == DEADLINE_SYNC_REQUIRED;
      case CANCEL_PENDING ->
          next == USER_CANCELLED || next == PENDING || next == DEADLINE_REFUND_AVAILABLE;
      case REJECT_PENDING ->
          next == REJECTED || next == PENDING || next == DEADLINE_REFUND_AVAILABLE;
      case CONFIRM_PENDING ->
          next == SETTLED || next == APPROVED || next == DEADLINE_REFUND_AVAILABLE;
      case DEADLINE_REFUND_AVAILABLE -> next == DEADLINE_REFUND_PENDING;
      case DEADLINE_REFUND_PENDING ->
          next == DEADLINE_REFUNDED || next == DEADLINE_REFUND_AVAILABLE;
      case DEADLINE_RECOVERY_REQUIRED, DEADLINE_SYNC_REQUIRED -> next == DEADLINE_REFUND_AVAILABLE;
      case MANUAL_SYNC_REQUIRED ->
          next == USER_CANCELLED
              || next == REJECTED
              || next == TIMEOUT_CANCELLED
              || next == SETTLED
              || next == AUTO_SETTLED
              || next == DEADLINE_REFUNDED
              || next == DEADLINE_REFUND_AVAILABLE;
      default -> false;
    };
  }
}
