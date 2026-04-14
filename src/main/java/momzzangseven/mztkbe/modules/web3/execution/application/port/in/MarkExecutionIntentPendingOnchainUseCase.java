package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

public interface MarkExecutionIntentPendingOnchainUseCase {

  void execute(Long submittedTxId);
}
