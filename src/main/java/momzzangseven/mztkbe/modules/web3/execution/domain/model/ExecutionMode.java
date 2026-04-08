package momzzangseven.mztkbe.modules.web3.execution.domain.model;

public enum ExecutionMode {
  EIP7702(2),
  EIP1559(1);

  private final int requiredSignCount;

  ExecutionMode(int requiredSignCount) {
    this.requiredSignCount = requiredSignCount;
  }

  public int requiredSignCount() {
    return requiredSignCount;
  }
}
