package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** Pending user-side marketplace escrow mutation currently being prepared or executed. */
public enum ReservationEscrowAction {
  PURCHASE,
  BUYER_CANCEL,
  TRAINER_REJECT,
  BUYER_CONFIRM,
  DEADLINE_REFUND
}
