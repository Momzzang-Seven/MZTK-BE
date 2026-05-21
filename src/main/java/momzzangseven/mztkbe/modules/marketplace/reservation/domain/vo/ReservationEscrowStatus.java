package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** On-chain escrow substate for user-managed marketplace reservation flows. */
public enum ReservationEscrowStatus {
  NONE,
  PURCHASE_PREPARING,
  PURCHASE_PENDING,
  LOCKED,
  CANCEL_PENDING,
  REJECT_PENDING,
  CONFIRM_PENDING,
  ADMIN_REFUND_PENDING,
  ADMIN_SETTLE_PENDING,
  DEADLINE_REFUND_AVAILABLE,
  DEADLINE_REFUND_PENDING,
  REFUNDED,
  SETTLED,
  DEADLINE_REFUNDED,
  DEADLINE_RECOVERY_REQUIRED,
  DEADLINE_SYNC_REQUIRED,
  MANUAL_SYNC_REQUIRED,
  HOLD_EXPIRED,
  PAYMENT_FAILED,
  FAILED
}
