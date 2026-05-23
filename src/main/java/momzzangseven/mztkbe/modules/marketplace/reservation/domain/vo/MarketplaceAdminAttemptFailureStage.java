package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/** Derived stage for the latest marketplace admin execution attempt failure. */
public enum MarketplaceAdminAttemptFailureStage {
  PHASE_B_PREFLIGHT,
  PHASE_B_CHAIN_MISMATCH,
  PHASE_C_RELOCK,
  PHASE_C_BIND,
  PHASE_C_SUBMIT,
  RECOVERY_EXPIRED_PREPARATION,
  EXECUTION_TERMINATED,
  UNKNOWN
}
