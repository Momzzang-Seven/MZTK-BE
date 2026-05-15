package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

/** Schedules best-effort work after the current transaction commits. */
public interface RunAfterCommitPort {

  void runAfterCommit(Runnable action);
}
