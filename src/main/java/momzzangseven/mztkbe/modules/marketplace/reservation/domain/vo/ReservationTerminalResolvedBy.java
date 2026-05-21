package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** Actor category that resolved a marketplace reservation into a terminal/sync state. */
public enum ReservationTerminalResolvedBy {
  ADMIN,
  SCHEDULER,
  CHAIN_SYNC
}
