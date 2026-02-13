package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;

/** Port for claiming transaction work with lock TTL semantics. */
public interface LoadTransactionWorkPort {

  List<TransactionWorkItem> claimByStatus(
      Web3TxStatus status, int limit, String workerId, Duration claimTtl);

  record TransactionWorkItem(
      Long transactionId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      Long nonce,
      String txHash,
      String signedRawTx,
      String failureReason,
      LocalDateTime broadcastedAt) {

    public TransactionWorkItem {
      validate(transactionId, fromAddress, toAddress, amountWei, nonce);
    }

    private static void validate(
        Long transactionId,
        String fromAddress,
        String toAddress,
        BigInteger amountWei,
        Long nonce) {
      if (transactionId == null || transactionId <= 0) {
        throw new Web3InvalidInputException("transactionId must be positive");
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
