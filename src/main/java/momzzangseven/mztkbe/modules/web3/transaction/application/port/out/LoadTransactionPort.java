package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

public interface LoadTransactionPort {

  Optional<TransactionSnapshot> loadById(Long transactionId);

  List<TransactionSnapshot> loadLevelRewardsByReferenceIds(Collection<String> referenceIds);

  record TransactionSnapshot(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      Web3TxStatus status,
      String txHash,
      String failureReason) {

    public TransactionSnapshot {
      validate(transactionId, idempotencyKey, referenceType, referenceId, status, txHash);
    }

    private static void validate(
        Long transactionId,
        String idempotencyKey,
        Web3ReferenceType referenceType,
        String referenceId,
        Web3TxStatus status,
        String txHash) {
      if (transactionId == null || transactionId <= 0) {
        throw new Web3InvalidInputException("transactionId must be positive");
      }
      if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new Web3InvalidInputException("idempotencyKey is required");
      }
      if (referenceType == null) {
        throw new Web3InvalidInputException("referenceType is required");
      }
      if (referenceId == null || referenceId.isBlank()) {
        throw new Web3InvalidInputException("referenceId is required");
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
