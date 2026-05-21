package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Operator-facing phase for marketplace admin execution polling. */
public enum MarketplaceAdminExecutionPhase {
  IDLE,
  QUEUED_FOR_SERVER_RELAYER,
  PENDING_ONCHAIN,
  CONFIRMED_PENDING_LOCAL_SYNC,
  COMPLETED,
  FAILED_ONCHAIN,
  EXPIRED,
  DEADLINE_SYNC_REQUIRED,
  MANUAL_SYNC_REQUIRED
}
