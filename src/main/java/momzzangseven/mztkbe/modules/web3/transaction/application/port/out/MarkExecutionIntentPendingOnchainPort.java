package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

public interface MarkExecutionIntentPendingOnchainPort {

  void markPendingOnchain(Long transactionId);
}
