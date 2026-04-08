package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

public interface MarkExecutionIntentFailedOnchainUseCase {

  void execute(Long submittedTxId, String failureReason);
}
