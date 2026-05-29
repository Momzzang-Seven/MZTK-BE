package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

public enum MarketplaceWeb3AutoSettleSkipCategory {
  NONE,
  LOCK_OR_STATE_RACE,
  NON_RETRYABLE_PREPARATION_FAILED,
  DEADLINE_OR_CHAIN_PRECONDITION,
  UNRESOLVED_EXECUTION_GUARD,
  UNKNOWN_STATE_SKIP
}
