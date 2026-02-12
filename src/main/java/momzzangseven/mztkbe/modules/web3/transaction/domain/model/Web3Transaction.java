package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;

/** Domain model for web3_transactions row. */
@Getter
@Builder(toBuilder = true)
public class Web3Transaction {
  private Long id;
  private String idempotencyKey;
  private Web3ReferenceType referenceType;
  private String referenceId;

  private Long fromUserId;
  private Long toUserId;
  private String fromAddress;
  private String toAddress;
  private BigInteger amountWei;
  private Long nonce;

  private Web3TxStatus status;
  private String txHash;
  private LocalDateTime signedAt;
  private LocalDateTime broadcastedAt;
  private LocalDateTime confirmedAt;
  private String signedRawTx;
  private String failureReason;

  private LocalDateTime processingUntil;
  private String processingBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public String referenceKey() {
    return referenceType + ":" + referenceId;
  }

  public void assignNonce(long assignedNonce) {
    if (assignedNonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (status != Web3TxStatus.CREATED) {
      throw new Web3TransactionStateInvalidException(
          "nonce can only be assigned in CREATED status: current=" + status);
    }
    if (nonce != null && !nonce.equals(assignedNonce)) {
      throw new Web3TransactionStateInvalidException(
          "nonce already assigned: existing=" + nonce + ", requested=" + assignedNonce);
    }
    nonce = assignedNonce;
  }

  public void markSigned(long signedNonce, String rawTx, String hash, LocalDateTime now) {
    assertTransitionAllowed(Web3TxStatus.SIGNED);
    if (rawTx == null || rawTx.isBlank()) {
      throw new Web3InvalidInputException("signedRawTx is required");
    }
    if (signedNonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    status = Web3TxStatus.SIGNED;
    nonce = signedNonce;
    signedRawTx = rawTx;
    if (hash != null && !hash.isBlank()) {
      txHash = hash;
    }
    failureReason = null;
    if (signedAt == null) {
      signedAt = now == null ? LocalDateTime.now() : now;
    }
    clearProcessingLock();
  }

  public void markPending(String hash, LocalDateTime now) {
    assertTransitionAllowed(Web3TxStatus.PENDING);
    if (hash == null || hash.isBlank()) {
      throw new Web3InvalidInputException("txHash is required");
    }
    status = Web3TxStatus.PENDING;
    txHash = hash;
    failureReason = null;
    if (broadcastedAt == null) {
      broadcastedAt = now == null ? LocalDateTime.now() : now;
    }
    clearProcessingLock();
  }

  public void updateStatus(
      Web3TxStatus nextStatus, String hash, String reason, LocalDateTime nowForState) {
    if (nextStatus == null) {
      throw new Web3InvalidInputException("nextStatus is required");
    }
    assertTransitionAllowed(nextStatus);
    status = nextStatus;
    if (hash != null && !hash.isBlank()) {
      txHash = hash;
    }
    failureReason = reason;
    LocalDateTime now = nowForState == null ? LocalDateTime.now() : nowForState;

    if (nextStatus == Web3TxStatus.SIGNED && signedAt == null) {
      signedAt = now;
    }
    if (nextStatus == Web3TxStatus.PENDING && broadcastedAt == null) {
      broadcastedAt = now;
    }
    if ((nextStatus == Web3TxStatus.SUCCEEDED || nextStatus == Web3TxStatus.FAILED_ONCHAIN)
        && confirmedAt == null) {
      confirmedAt = now;
    }
    clearProcessingLock();
  }

  public void scheduleRetry(String reason, LocalDateTime until) {
    failureReason = reason;
    processingBy = null;
    processingUntil = until;
  }

  public void clearProcessingLock() {
    processingBy = null;
    processingUntil = null;
  }

  private void assertTransitionAllowed(Web3TxStatus nextStatus) {
    if (status == nextStatus) {
      return;
    }

    boolean allowed =
        switch (status) {
          case CREATED -> nextStatus == Web3TxStatus.SIGNED;
          case SIGNED -> nextStatus == Web3TxStatus.PENDING;
          case PENDING ->
              nextStatus == Web3TxStatus.SUCCEEDED
                  || nextStatus == Web3TxStatus.FAILED_ONCHAIN
                  || nextStatus == Web3TxStatus.UNCONFIRMED;
          case UNCONFIRMED -> nextStatus == Web3TxStatus.SUCCEEDED;
          case SUCCEEDED, FAILED_ONCHAIN -> false;
        };

    if (!allowed) {
      throw new Web3TransactionStateInvalidException(
          "invalid transition: " + status + " -> " + nextStatus);
    }
  }
}
