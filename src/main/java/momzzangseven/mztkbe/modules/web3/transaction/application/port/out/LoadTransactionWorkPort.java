package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

/** Port for claiming transaction work with lock TTL semantics. */
public interface LoadTransactionWorkPort {

  List<TransactionWorkItem> claimByStatus(
      Web3TxStatus status, int limit, String workerId, Duration claimTtl);

  record TransactionWorkItem(
      Long transactionId,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      Long nonce,
      String txHash,
      String signedRawTx,
      String failureReason,
      LocalDateTime broadcastedAt) {

    public TransactionWorkItem {
      validate(
          transactionId,
          idempotencyKey,
          referenceType,
          referenceId,
          fromAddress,
          toAddress,
          amountWei,
          nonce);
    }

    private static void validate(
        Long transactionId,
        String idempotencyKey,
        Web3ReferenceType referenceType,
        String referenceId,
        String fromAddress,
        String toAddress,
        BigInteger amountWei,
        Long nonce) {
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
      if (fromAddress == null || fromAddress.isBlank()) {
        throw new Web3InvalidInputException("fromAddress is required");
      }
      if (toAddress == null || toAddress.isBlank()) {
        throw new Web3InvalidInputException("toAddress is required");
      }
      if (amountWei == null || amountWei.signum() < 0) {
        throw new Web3InvalidInputException("amountWei must be non-negative");
      }
      if (nonce != null && nonce < 0) {
        throw new Web3InvalidInputException("nonce must be >= 0");
      }
    }
  }
}
