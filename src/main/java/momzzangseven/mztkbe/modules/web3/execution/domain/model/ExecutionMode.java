package momzzangseven.mztkbe.modules.web3.execution.domain.model;

/** Execution signing/broadcast mode selected at intent creation time. */
public enum ExecutionMode {
  EIP7702(2),
  EIP1559(1);

  private final int requiredSignCount;

  ExecutionMode(int requiredSignCount) {
    this.requiredSignCount = requiredSignCount;
  }

  /** Number of user signatures required for the mode-specific execute request. */
  public int requiredSignCount() {
    return requiredSignCount;
  }
}
