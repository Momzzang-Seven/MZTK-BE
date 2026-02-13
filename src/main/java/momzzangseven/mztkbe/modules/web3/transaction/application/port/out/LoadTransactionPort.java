package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public interface LoadTransactionPort {

  Optional<TransactionSnapshot> loadById(Long transactionId);

  record TransactionSnapshot(
      Long transactionId, Web3TxStatus status, String txHash, String failureReason) {

    public TransactionSnapshot {
      validate(transactionId, status, txHash);
    }

    private static void validate(Long transactionId, Web3TxStatus status, String txHash) {
      if (transactionId == null || transactionId <= 0) {
        throw new Web3InvalidInputException("transactionId must be positive");
      }
      if (status == null) {
        throw new Web3InvalidInputException("status is required");
      }
      if (txHash != null && !txHash.isBlank() && !txHash.matches("^0x[0-9a-fA-F]{64}$")) {
        throw new Web3InvalidInputException("txHash must be 0x-prefixed 32-byte hex");
      }
    }
  }
}
