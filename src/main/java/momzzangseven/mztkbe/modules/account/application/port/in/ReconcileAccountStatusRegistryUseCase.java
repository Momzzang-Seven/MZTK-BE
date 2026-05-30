package momzzangseven.mztkbe.modules.account.application.port.in;

/**
 * Input port that rebuilds the entire in-memory denylist from the DB snapshot of non-ACTIVE users.
 * Driven by warmup at startup and by the periodic reconcile schedule.
 */
public interface ReconcileAccountStatusRegistryUseCase {

  /** Rebuilds the denylist from the authoritative DB snapshot of non-ACTIVE users. */
  void reconcile();
}
