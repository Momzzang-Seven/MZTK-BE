package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

/** Runs execution hook state mutation inside an infrastructure-owned transaction boundary. */
public interface RunExecutionHookTransactionPort {

  void requiresNew(Runnable action);
}
