package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.springframework.data.domain.Page;

public interface ManageTransactionRecoveryPort {

  Optional<RecoverySnapshot> loadByIdForUpdate(Long transactionId);

  RequeueMutation clearFailureForRequeue(Long transactionId);

  Page<RecoverySnapshot> loadPage(RecoveryQuery query);

  record RecoveryQuery(
      Web3TxStatus status,
      String failureReason,
      Web3ReferenceType referenceType,
      String referenceId,
      Web3TxType txType,
      int page,
      int size) {

    public RecoveryQuery {
      if (page < 0) {
        throw new Web3InvalidInputException("page must be >= 0");
      }
      if (size <= 0) {
        throw new Web3InvalidInputException("size must be > 0");
      }
      if (referenceId != null && referenceId.isBlank()) {
        throw new Web3InvalidInputException("referenceId must not be blank");
      }
    }
  }

  record RecoverySnapshot(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Web3TxType txType,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      Web3TxStatus status,
      String txHash,
      String failureReason,
      Long nonce,
      String signedRawTx,
      LocalDateTime signedAt,
      LocalDateTime broadcastedAt,
      LocalDateTime confirmedAt,
      String processingBy,
      LocalDateTime processingUntil,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {

    public RecoverySnapshot {
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
      if (txType == null) {
        throw new Web3InvalidInputException("txType is required");
      }
      if (status == null) {
        throw new Web3InvalidInputException("status is required");
      }
    }
  }

  record RequeueMutation(
      Long transactionId,
      Web3TxStatus status,
      Web3TxStatus previousStatus,
      String originalFailureReason) {

    public RequeueMutation {
      if (transactionId == null || transactionId <= 0) {
        throw new Web3InvalidInputException("transactionId must be positive");
      }
      if (status == null) {
        throw new Web3InvalidInputException("status is required");
      }
      if (previousStatus == null) {
        throw new Web3InvalidInputException("previousStatus is required");
      }
      if (originalFailureReason == null || originalFailureReason.isBlank()) {
        throw new Web3InvalidInputException("originalFailureReason is required");
      }
    }
  }
}
