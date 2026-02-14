package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

/** Port for changing transaction status and processing locks. */
public interface UpdateTransactionPort {

  void assignNonce(Long transactionId, long nonce);

  void markSigned(Long transactionId, long nonce, String signedRawTx, String txHash);

  void markPending(Long transactionId, String txHash);

  void updateStatus(Long transactionId, Web3TxStatus status, String txHash, String failureReason);

  void scheduleRetry(Long transactionId, String failureReason, LocalDateTime processingUntil);

  void clearProcessingLock(Long transactionId);
}
