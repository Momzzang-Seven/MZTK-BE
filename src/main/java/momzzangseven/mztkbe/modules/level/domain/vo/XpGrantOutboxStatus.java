package momzzangseven.mztkbe.modules.level.domain.vo;

/** Lifecycle of an XP-grant outbox row used for guaranteed retry of failed synchronous grants. */
public enum XpGrantOutboxStatus {
  /** Awaiting (re)processing by the reconciliation scheduler. */
  PENDING,
  /** Grant was applied (or proven already applied) — terminal success. */
  DONE,
  /** Retry budget exhausted — terminal failure requiring manual attention. */
  FAILED
}
