package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

/** Schedules best-effort work after the current transaction commits. */
public interface RunAfterCommitPort {

  void runAfterCommit(Runnable action);

  /**
   * Schedules work after commit without opening a transaction around the action itself.
   *
   * <p>Use this for slow external I/O such as raw transaction broadcast. If the follow-up needs DB
   * writes, call {@link #runAfterCommit(Runnable)} from inside the action after the external call
   * returns.
   */
  default void runAfterCommitWithoutTransaction(Runnable action) {
    runAfterCommit(action);
  }
}
