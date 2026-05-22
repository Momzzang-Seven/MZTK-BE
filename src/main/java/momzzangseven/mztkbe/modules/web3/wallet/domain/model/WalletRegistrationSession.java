package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Durable wallet-registration session between ownership verification and final ACTIVE wallet
 * creation.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WalletRegistrationSession {

  private static final String RECEIPT_TIMEOUT = "RECEIPT_TIMEOUT";

  private final Long id;
  private final String publicId;
  private final Long userId;
  private final String walletAddress;
  private final String challengeNonce;
  private final WalletRegistrationStatus status;
  private final String latestExecutionIntentId;
  private final String receiptTimeoutExecutionIntentIds;
  private final Long latestTransactionId;
  private final String latestTransactionHash;
  private final String lastExecutionStatus;
  private final String lastErrorCode;
  private final String lastErrorReason;
  private final Integer retryCount;
  private final LocalDateTime approvalExpiresAt;
  private final LocalDateTime submittedAt;
  private final LocalDateTime confirmedAt;
  private final LocalDateTime finalizedAt;
  private final Long registeredWalletId;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  /** Creates a new session before the approval execution intent is attached. */
  public static WalletRegistrationSession create(
      String publicId,
      Long userId,
      String walletAddress,
      String challengeNonce,
      LocalDateTime approvalExpiresAt,
      LocalDateTime now) {
    validateIdentity(publicId, userId, walletAddress, challengeNonce);
    requireNow(now);
    if (approvalExpiresAt == null || !approvalExpiresAt.isAfter(now)) {
      throw new Web3InvalidInputException("approvalExpiresAt must be after now");
    }

    return WalletRegistrationSession.builder()
        .publicId(publicId)
        .userId(userId)
        .walletAddress(walletAddress)
        .challengeNonce(challengeNonce)
        .status(WalletRegistrationStatus.APPROVAL_REQUIRED)
        .retryCount(0)
        .approvalExpiresAt(approvalExpiresAt)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /** Attaches or replaces the approval execution intent while the session is still retryable. */
  public WalletRegistrationSession attachApprovalIntent(
      String executionIntentId, LocalDateTime nextApprovalExpiresAt, LocalDateTime now) {
    requireExecutableStatus();
    requireExecutionIntentId(executionIntentId);
    requireNow(now);
    if (nextApprovalExpiresAt == null || !nextApprovalExpiresAt.isAfter(now)) {
      throw new Web3InvalidInputException("nextApprovalExpiresAt must be after now");
    }
    if (status == WalletRegistrationStatus.APPROVAL_REQUIRED && latestExecutionIntentId != null) {
      throw new IllegalStateException("approval intent is already attached");
    }

    int nextRetryCount =
        status == WalletRegistrationStatus.APPROVAL_RETRYABLE
            ? safeRetryCount() + 1
            : safeRetryCount();

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_REQUIRED)
        .latestExecutionIntentId(executionIntentId)
        .latestTransactionId(null)
        .latestTransactionHash(null)
        .lastExecutionStatus(null)
        .lastErrorCode(null)
        .lastErrorReason(null)
        .retryCount(nextRetryCount)
        .approvalExpiresAt(nextApprovalExpiresAt)
        .updatedAt(now)
        .build();
  }

  /** Attaches or replaces the approval execution intent while preserving the session deadline. */
  public WalletRegistrationSession attachApprovalIntentPreservingDeadline(
      String executionIntentId, LocalDateTime now) {
    requireExecutableStatus();
    requireExecutionIntentId(executionIntentId);
    requireNow(now);
    if (approvalExpiresAt == null || !approvalExpiresAt.isAfter(now)) {
      throw new Web3InvalidInputException("approvalExpiresAt must be after now");
    }
    if (status == WalletRegistrationStatus.APPROVAL_REQUIRED && latestExecutionIntentId != null) {
      throw new IllegalStateException("approval intent is already attached");
    }

    int nextRetryCount =
        status == WalletRegistrationStatus.APPROVAL_RETRYABLE
            ? safeRetryCount() + 1
            : safeRetryCount();

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_REQUIRED)
        .latestExecutionIntentId(executionIntentId)
        .latestTransactionId(null)
        .latestTransactionHash(null)
        .lastExecutionStatus(null)
        .lastErrorCode(null)
        .lastErrorReason(null)
        .retryCount(nextRetryCount)
        .updatedAt(now)
        .build();
  }

  /** Marks that the approval signature was accepted and transaction submission began. */
  public WalletRegistrationSession markApprovalSigned(
      String executionIntentId,
      Long transactionId,
      String transactionHash,
      String executionStatus,
      LocalDateTime now) {
    requireStatus(WalletRegistrationStatus.APPROVAL_REQUIRED);
    requireLatestExecutionIntent(executionIntentId);
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_SIGNED)
        .latestTransactionId(transactionId)
        .latestTransactionHash(transactionHash)
        .lastExecutionStatus(executionStatus)
        .submittedAt(now)
        .updatedAt(now)
        .build();
  }

  /** Marks that the approval transaction is pending on-chain. */
  public WalletRegistrationSession markApprovalPendingOnchain(
      String executionIntentId,
      Long transactionId,
      String transactionHash,
      String executionStatus,
      LocalDateTime now) {
    requireStatus(WalletRegistrationStatus.APPROVAL_SIGNED);
    requireLatestExecutionIntent(executionIntentId);
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN)
        .latestTransactionId(transactionId)
        .latestTransactionHash(transactionHash)
        .lastExecutionStatus(executionStatus)
        .submittedAt(submittedAt == null ? now : submittedAt)
        .updatedAt(now)
        .build();
  }

  /** Records that the latest approval execution was confirmed before local finalization runs. */
  public WalletRegistrationSession markApprovalConfirmed(
      String executionIntentId,
      Long transactionId,
      String transactionHash,
      String executionStatus,
      LocalDateTime now) {
    requireLatestExecutionIntent(executionIntentId);
    requireNow(now);
    if (status != WalletRegistrationStatus.APPROVAL_REQUIRED
        && status != WalletRegistrationStatus.APPROVAL_SIGNED
        && status != WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && !status.isConfirmedButNotFinalized()
        && !isReceiptTimeoutLateSuccessStatus()) {
      throw new IllegalStateException("session cannot be confirmed from " + status);
    }

    WalletRegistrationStatus nextStatus =
        status.isConfirmedButNotFinalized()
            ? status
            : WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN;

    return toBuilder()
        .status(nextStatus)
        .latestTransactionId(transactionId == null ? latestTransactionId : transactionId)
        .latestTransactionHash(
            transactionHash == null || transactionHash.isBlank()
                ? latestTransactionHash
                : transactionHash)
        .lastExecutionStatus(executionStatus)
        .submittedAt(submittedAt == null ? now : submittedAt)
        .confirmedAt(confirmedAt == null ? now : confirmedAt)
        .updatedAt(now)
        .build();
  }

  /**
   * Recovers a same-registration approval confirmation that arrived after a receipt-timeout retry
   * replaced the latest intent.
   */
  public WalletRegistrationSession markRecoveredApprovalConfirmed(
      String executionIntentId, String executionStatus, LocalDateTime now) {
    requireExecutionIntentId(executionIntentId);
    requireNow(now);
    if (!canRecoverReplacedApprovalIntent(executionIntentId)) {
      throw new IllegalStateException("session cannot recover replaced approval from " + status);
    }

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN)
        .latestExecutionIntentId(executionIntentId)
        .latestTransactionId(null)
        .latestTransactionHash(null)
        .lastExecutionStatus(executionStatus)
        .submittedAt(submittedAt == null ? now : submittedAt)
        .confirmedAt(confirmedAt == null ? now : confirmedAt)
        .updatedAt(now)
        .build();
  }

  /** Marks local wallet creation as complete after approval confirmation. */
  public WalletRegistrationSession markRegistered(Long walletId, LocalDateTime now) {
    if (status != WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && !status.isConfirmedButNotFinalized()) {
      throw new IllegalStateException("session cannot be registered from " + status);
    }
    if (walletId == null || walletId <= 0) {
      throw new Web3InvalidInputException("walletId must be positive");
    }
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.REGISTERED)
        .registeredWalletId(walletId)
        .confirmedAt(confirmedAt == null ? now : confirmedAt)
        .finalizedAt(now)
        .lastErrorCode(null)
        .lastErrorReason(null)
        .updatedAt(now)
        .build();
  }

  /** Records approval execution failure that can be retried by creating a new intent later. */
  public WalletRegistrationSession markApprovalRetryable(
      String errorCode, String errorReason, LocalDateTime now) {
    if (status == WalletRegistrationStatus.FINALIZATION_FAILED
        || status == WalletRegistrationStatus.LOCAL_CONFLICT
        || isTerminal()) {
      throw new IllegalStateException("session cannot become retryable from " + status);
    }
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_RETRYABLE)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .receiptTimeoutExecutionIntentIds(rememberReceiptTimeoutIntent(errorCode))
        .updatedAt(now)
        .build();
  }

  /** Records a terminal approval failure. */
  public WalletRegistrationSession markApprovalFailed(
      String errorCode, String errorReason, LocalDateTime now) {
    if (isTerminal()) {
      throw new IllegalStateException("terminal session cannot fail again");
    }
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.APPROVAL_FAILED)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .receiptTimeoutExecutionIntentIds(rememberReceiptTimeoutIntent(errorCode))
        .updatedAt(now)
        .build();
  }

  /** Expires only pre-submission sessions. */
  public WalletRegistrationSession expire(String errorCode, String errorReason, LocalDateTime now) {
    if (!status.isPreSubmissionExpirable()) {
      throw new IllegalStateException("session cannot expire from " + status);
    }
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.EXPIRED)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  /** Cancels only pre-submission sessions. */
  public WalletRegistrationSession cancel(String errorCode, String errorReason, LocalDateTime now) {
    if (!status.isPreSubmissionExpirable()) {
      throw new IllegalStateException("session cannot be canceled from " + status);
    }
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.CANCELED)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  /** Records that approval was confirmed but local finalization failed unexpectedly. */
  public WalletRegistrationSession markFinalizationFailed(
      String errorCode, String errorReason, LocalDateTime now) {
    requireFinalizationFailureStatus();
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.FINALIZATION_FAILED)
        .confirmedAt(confirmedAt == null ? now : confirmedAt)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  /** Records that approval was confirmed but local wallet state conflicted with DB constraints. */
  public WalletRegistrationSession markLocalConflict(
      String errorCode, String errorReason, LocalDateTime now) {
    requireFinalizationFailureStatus();
    requireNow(now);

    return toBuilder()
        .status(WalletRegistrationStatus.LOCAL_CONFLICT)
        .confirmedAt(confirmedAt == null ? now : confirmedAt)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  /** Returns whether a new approval execution intent may be created. */
  public boolean canCreateApprovalIntent() {
    return status == WalletRegistrationStatus.APPROVAL_REQUIRED && latestExecutionIntentId == null
        || status == WalletRegistrationStatus.APPROVAL_RETRYABLE;
  }

  /** Returns whether the session is terminal for registration lifecycle purposes. */
  public boolean isTerminal() {
    return !status.isNonTerminal();
  }

  /** Returns whether an older intent is recorded as receipt-timeout recovery evidence. */
  public boolean hasReceiptTimeoutExecutionIntent(String executionIntentId) {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      return false;
    }
    if (receiptTimeoutExecutionIntentIds == null || receiptTimeoutExecutionIntentIds.isBlank()) {
      return false;
    }
    for (String recorded : receiptTimeoutExecutionIntentIds.split(",")) {
      if (executionIntentId.equals(recorded)) {
        return true;
      }
    }
    return false;
  }

  private void requireExecutableStatus() {
    if (!canCreateApprovalIntent()) {
      throw new IllegalStateException("approval intent cannot be created from " + status);
    }
  }

  private void requireLatestExecutionIntent(String executionIntentId) {
    requireExecutionIntentId(executionIntentId);
    if (!executionIntentId.equals(latestExecutionIntentId)) {
      throw new IllegalStateException("execution intent does not match latest session intent");
    }
  }

  private void requireStatus(WalletRegistrationStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("expected " + expected + " but was " + status);
    }
  }

  private void requireFinalizationFailureStatus() {
    if (status != WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
        && !status.isConfirmedButNotFinalized()
        && !isReceiptTimeoutLateSuccessStatus()) {
      throw new IllegalStateException("session cannot record finalization failure from " + status);
    }
  }

  private boolean isReceiptTimeoutLateSuccessStatus() {
    return (status == WalletRegistrationStatus.APPROVAL_RETRYABLE
            || status == WalletRegistrationStatus.APPROVAL_FAILED)
        && RECEIPT_TIMEOUT.equals(lastErrorCode);
  }

  private boolean canRecoverReplacedApprovalIntent(String executionIntentId) {
    return hasReceiptTimeoutExecutionIntent(executionIntentId)
        && (status == WalletRegistrationStatus.APPROVAL_REQUIRED
            || status == WalletRegistrationStatus.APPROVAL_SIGNED
            || status == WalletRegistrationStatus.APPROVAL_PENDING_ONCHAIN
            || status == WalletRegistrationStatus.APPROVAL_RETRYABLE
            || status == WalletRegistrationStatus.APPROVAL_FAILED
            || status.isConfirmedButNotFinalized());
  }

  private static void requireExecutionIntentId(String executionIntentId) {
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
  }

  private static void requireNow(LocalDateTime now) {
    if (now == null) {
      throw new Web3InvalidInputException("now is required");
    }
  }

  private int safeRetryCount() {
    return retryCount == null ? 0 : retryCount;
  }

  private String rememberReceiptTimeoutIntent(String errorCode) {
    if (!RECEIPT_TIMEOUT.equals(errorCode)
        || latestExecutionIntentId == null
        || latestExecutionIntentId.isBlank()
        || hasReceiptTimeoutExecutionIntent(latestExecutionIntentId)) {
      return receiptTimeoutExecutionIntentIds;
    }
    if (receiptTimeoutExecutionIntentIds == null || receiptTimeoutExecutionIntentIds.isBlank()) {
      return latestExecutionIntentId;
    }
    return receiptTimeoutExecutionIntentIds + "," + latestExecutionIntentId;
  }

  private static void validateIdentity(
      String publicId, Long userId, String walletAddress, String challengeNonce) {
    if (publicId == null || publicId.isBlank()) {
      throw new Web3InvalidInputException("publicId is required");
    }
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new Web3InvalidInputException("walletAddress is required");
    }
    if (!walletAddress.equals(walletAddress.toLowerCase())) {
      throw new Web3InvalidInputException("walletAddress must be normalized to lowercase");
    }
    if (challengeNonce == null || challengeNonce.isBlank()) {
      throw new Web3InvalidInputException("challengeNonce is required");
    }
  }
}
