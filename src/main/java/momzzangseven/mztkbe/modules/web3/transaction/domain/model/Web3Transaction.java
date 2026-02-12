package momzzangseven.mztkbe.modules.web3.transaction.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.global.error.web3.Web3ValidationMessage;

/** Domain model for web3_transactions row. */
@Getter
@Builder(access = AccessLevel.PRIVATE, toBuilder = true)
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

  public static Web3Transaction createIntent(
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      LocalDateTime now) {
    validateCore(idempotencyKey, referenceType, referenceId, fromAddress, toAddress, amountWei);
    if (now == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.NOW_REQUIRED);
    }
    return Web3Transaction.builder()
        .idempotencyKey(idempotencyKey)
        .referenceType(referenceType)
        .referenceId(referenceId)
        .fromUserId(fromUserId)
        .toUserId(toUserId)
        .fromAddress(fromAddress)
        .toAddress(toAddress)
        .amountWei(amountWei)
        .status(Web3TxStatus.CREATED)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public static Web3Transaction reconstitute(
      Long id,
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      Long fromUserId,
      Long toUserId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei,
      Long nonce,
      Web3TxStatus status,
      String txHash,
      LocalDateTime signedAt,
      LocalDateTime broadcastedAt,
      LocalDateTime confirmedAt,
      String signedRawTx,
      String failureReason,
      LocalDateTime processingUntil,
      String processingBy,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    validateCore(idempotencyKey, referenceType, referenceId, fromAddress, toAddress, amountWei);
    if (status == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.STATUS_REQUIRED);
    }
    return Web3Transaction.builder()
        .id(id)
        .idempotencyKey(idempotencyKey)
        .referenceType(referenceType)
        .referenceId(referenceId)
        .fromUserId(fromUserId)
        .toUserId(toUserId)
        .fromAddress(fromAddress)
        .toAddress(toAddress)
        .amountWei(amountWei)
        .nonce(nonce)
        .status(status)
        .txHash(txHash)
        .signedAt(signedAt)
        .broadcastedAt(broadcastedAt)
        .confirmedAt(confirmedAt)
        .signedRawTx(signedRawTx)
        .failureReason(failureReason)
        .processingUntil(processingUntil)
        .processingBy(processingBy)
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .build();
  }

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
    requireCurrentStatus(Web3TxStatus.CREATED, "markSigned");
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
      if (now == null) {
        throw new Web3InvalidInputException(Web3ValidationMessage.NOW_REQUIRED);
      }
      signedAt = now;
    }
    clearProcessingLock();
  }

  public void markPending(String hash, LocalDateTime now) {
    requireCurrentStatus(Web3TxStatus.SIGNED, "markPending");
    assertTransitionAllowed(Web3TxStatus.PENDING);
    if (hash == null || hash.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.TX_HASH_REQUIRED);
    }
    status = Web3TxStatus.PENDING;
    txHash = hash;
    failureReason = null;
    if (broadcastedAt == null) {
      if (now == null) {
        throw new Web3InvalidInputException(Web3ValidationMessage.NOW_REQUIRED);
      }
      broadcastedAt = now;
    }
    clearProcessingLock();
  }

  public void updateStatus(
      Web3TxStatus nextStatus, String hash, String reason, LocalDateTime nowForState) {
    if (nextStatus == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.NEXT_STATUS_REQUIRED);
    }
    assertTransitionAllowed(nextStatus);
    if (nowForState == null) {
      throw new Web3InvalidInputException(Web3ValidationMessage.NOW_REQUIRED);
    }
    status = nextStatus;
    if (hash != null && !hash.isBlank()) {
      txHash = hash;
    }
    failureReason = reason;
    LocalDateTime now = nowForState;

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
      throw new Web3TransactionStateInvalidException(
          "same-state transition is not allowed: " + status);
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

  private void requireCurrentStatus(Web3TxStatus expected, String action) {
    if (status != expected) {
      throw new Web3TransactionStateInvalidException(
          action + " requires " + expected + " status: current=" + status);
    }
  }

  private static void validateCore(
      String idempotencyKey,
      Web3ReferenceType referenceType,
      String referenceId,
      String fromAddress,
      String toAddress,
      BigInteger amountWei) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.IDEMPOTENCY_KEY_REQUIRED);
    }
    if (referenceType == null) {
      throw new Web3InvalidInputException("referenceType is required");
    }
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.FROM_ADDRESS_REQUIRED);
    }
    if (toAddress == null || toAddress.isBlank()) {
      throw new Web3InvalidInputException(Web3ValidationMessage.TO_ADDRESS_REQUIRED);
    }
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException(Web3ValidationMessage.AMOUNT_WEI_NON_NEGATIVE);
    }
  }
}
