package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

/**
 * Runs post-commit reservation side effects through an infrastructure-owned transaction boundary.
 */
public interface RunReservationPostCommitPort {

  void afterCommit(String callbackName, Runnable action);

  void requiresNew(Runnable action);
}
