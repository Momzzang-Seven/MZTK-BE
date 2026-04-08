package momzzangseven.mztkbe.modules.web3.execution.domain.model;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExecutionIntent {

  private final Long id;
  private final String publicId;
  private final String rootIdempotencyKey;
  private final Integer attemptNo;
  private final ExecutionResourceType resourceType;
  private final String resourceId;
  private final ExecutionActionType actionType;
  private final Long requesterUserId;
  private final Long counterpartyUserId;
  private final ExecutionMode mode;
  private final ExecutionIntentStatus status;
  private final String payloadHash;
  private final String payloadSnapshotJson;
  private final String authorityAddress;
  private final Long authorityNonce;
  private final String delegateTarget;
  private final LocalDateTime expiresAt;
  private final String authorizationPayloadHash;
  private final String executionDigest;
  private final UnsignedTxSnapshot unsignedTxSnapshot;
  private final String unsignedTxFingerprint;
  private final BigInteger reservedSponsorCostWei;
  private final LocalDate sponsorUsageDateKst;
  private final Long submittedTxId;
  private final String lastErrorCode;
  private final String lastErrorReason;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  public static ExecutionIntent create(
      String publicId,
      String rootIdempotencyKey,
      Integer attemptNo,
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionActionType actionType,
      Long requesterUserId,
      Long counterpartyUserId,
      ExecutionMode mode,
      String payloadHash,
      String payloadSnapshotJson,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      LocalDateTime expiresAt,
      String authorizationPayloadHash,
      String executionDigest,
      UnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint,
      BigInteger reservedSponsorCostWei,
      LocalDate sponsorUsageDateKst,
      LocalDateTime now) {
    validateCommon(
        publicId,
        rootIdempotencyKey,
        attemptNo,
        resourceType,
        resourceId,
        actionType,
        requesterUserId,
        mode,
        payloadHash,
        expiresAt);
    if (now == null) {
      throw new Web3InvalidInputException("now is required");
    }
    validateModeSpecific(
        mode,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authorizationPayloadHash,
        executionDigest,
        unsignedTxSnapshot,
        unsignedTxFingerprint);
    if (sponsorUsageDateKst == null) {
      throw new Web3InvalidInputException("sponsorUsageDateKst is required");
    }

    return ExecutionIntent.builder()
        .publicId(publicId)
        .rootIdempotencyKey(rootIdempotencyKey)
        .attemptNo(attemptNo)
        .resourceType(resourceType)
        .resourceId(resourceId)
        .actionType(actionType)
        .requesterUserId(requesterUserId)
        .counterpartyUserId(counterpartyUserId)
        .mode(mode)
        .status(ExecutionIntentStatus.AWAITING_SIGNATURE)
        .payloadHash(payloadHash)
        .payloadSnapshotJson(payloadSnapshotJson)
        .authorityAddress(authorityAddress)
        .authorityNonce(authorityNonce)
        .delegateTarget(delegateTarget)
        .expiresAt(expiresAt)
        .authorizationPayloadHash(authorizationPayloadHash)
        .executionDigest(executionDigest)
        .unsignedTxSnapshot(unsignedTxSnapshot)
        .unsignedTxFingerprint(unsignedTxFingerprint)
        .reservedSponsorCostWei(
            reservedSponsorCostWei == null ? BigInteger.ZERO : reservedSponsorCostWei)
        .sponsorUsageDateKst(sponsorUsageDateKst)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public boolean hasSamePayload(String candidatePayloadHash) {
    return payloadHash != null && payloadHash.equals(candidatePayloadHash);
  }

  public boolean isActiveForReuse() {
    return status == ExecutionIntentStatus.AWAITING_SIGNATURE
        || status == ExecutionIntentStatus.SIGNED
        || status == ExecutionIntentStatus.PENDING_ONCHAIN
        || status == ExecutionIntentStatus.CONFIRMED;
  }

  public boolean canStartNewAttempt() {
    return status == ExecutionIntentStatus.FAILED_ONCHAIN
        || status == ExecutionIntentStatus.EXPIRED
        || status == ExecutionIntentStatus.NONCE_STALE
        || status == ExecutionIntentStatus.CANCELED;
  }

  public boolean shouldExposeSignRequest() {
    return status == ExecutionIntentStatus.AWAITING_SIGNATURE;
  }

  public LocalDate resolveSponsorUsageDateKst() {
    if (sponsorUsageDateKst != null) {
      return sponsorUsageDateKst;
    }
    if (createdAt != null) {
      return createdAt.toLocalDate();
    }
    throw new IllegalStateException("sponsorUsageDateKst is missing");
  }

  public ExecutionIntent markSigned(Long nextSubmittedTxId, LocalDateTime now) {
    requireStatus(ExecutionIntentStatus.AWAITING_SIGNATURE);
    requireSubmittedTxId(nextSubmittedTxId);
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.SIGNED)
        .submittedTxId(nextSubmittedTxId)
        .updatedAt(now)
        .build();
  }

  public ExecutionIntent markPendingOnchain(Long nextSubmittedTxId, LocalDateTime now) {
    if (status != ExecutionIntentStatus.AWAITING_SIGNATURE
        && status != ExecutionIntentStatus.SIGNED) {
      throw new IllegalStateException("intent cannot move to PENDING_ONCHAIN from " + status);
    }
    requireSubmittedTxId(nextSubmittedTxId);
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.PENDING_ONCHAIN)
        .submittedTxId(nextSubmittedTxId)
        .updatedAt(now)
        .build();
  }

  public ExecutionIntent confirm(LocalDateTime now) {
    requireStatus(ExecutionIntentStatus.PENDING_ONCHAIN);
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.CONFIRMED)
        .updatedAt(now)
        .build();
  }

  public ExecutionIntent failOnchain(String errorCode, String errorReason, LocalDateTime now) {
    if (status != ExecutionIntentStatus.PENDING_ONCHAIN && status != ExecutionIntentStatus.SIGNED) {
      throw new IllegalStateException("intent cannot fail onchain from " + status);
    }
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.FAILED_ONCHAIN)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  public ExecutionIntent expire(String errorCode, String errorReason, LocalDateTime now) {
    requireStatus(ExecutionIntentStatus.AWAITING_SIGNATURE);
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.EXPIRED)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  public ExecutionIntent cancel(String errorCode, String errorReason, LocalDateTime now) {
    requireStatus(ExecutionIntentStatus.AWAITING_SIGNATURE);
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.CANCELED)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  public ExecutionIntent markNonceStale(String errorCode, String errorReason, LocalDateTime now) {
    requireStatus(ExecutionIntentStatus.AWAITING_SIGNATURE);
    requireNow(now);
    return toBuilder()
        .status(ExecutionIntentStatus.NONCE_STALE)
        .lastErrorCode(errorCode)
        .lastErrorReason(errorReason)
        .updatedAt(now)
        .build();
  }

  private void requireStatus(ExecutionIntentStatus expectedStatus) {
    if (status != expectedStatus) {
      throw new IllegalStateException(
          "execution intent requires " + expectedStatus + " status: current=" + status);
    }
  }

  private static void requireSubmittedTxId(Long submittedTxId) {
    if (submittedTxId == null || submittedTxId <= 0) {
      throw new Web3InvalidInputException("submittedTxId must be positive");
    }
  }

  private static void requireNow(LocalDateTime now) {
    if (now == null) {
      throw new Web3InvalidInputException("now is required");
    }
  }

  private static void validateCommon(
      String publicId,
      String rootIdempotencyKey,
      Integer attemptNo,
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionActionType actionType,
      Long requesterUserId,
      ExecutionMode mode,
      String payloadHash,
      LocalDateTime expiresAt) {
    if (publicId == null || publicId.isBlank()) {
      throw new Web3InvalidInputException("publicId is required");
    }
    if (rootIdempotencyKey == null || rootIdempotencyKey.isBlank()) {
      throw new Web3InvalidInputException("rootIdempotencyKey is required");
    }
    if (attemptNo == null || attemptNo <= 0) {
      throw new Web3InvalidInputException("attemptNo must be positive");
    }
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (mode == null) {
      throw new Web3InvalidInputException("mode is required");
    }
    if (payloadHash == null || payloadHash.isBlank()) {
      throw new Web3InvalidInputException("payloadHash is required");
    }
    if (expiresAt == null) {
      throw new Web3InvalidInputException("expiresAt is required");
    }
  }

  private static void validateModeSpecific(
      ExecutionMode mode,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      String authorizationPayloadHash,
      String executionDigest,
      UnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint) {
    if (mode == ExecutionMode.EIP7702) {
      if (authorityAddress == null || authorityAddress.isBlank()) {
        throw new Web3InvalidInputException("authorityAddress is required for EIP7702");
      }
      if (authorityNonce == null || authorityNonce < 0) {
        throw new Web3InvalidInputException("authorityNonce must be >= 0 for EIP7702");
      }
      if (delegateTarget == null || delegateTarget.isBlank()) {
        throw new Web3InvalidInputException("delegateTarget is required for EIP7702");
      }
      if (authorizationPayloadHash == null || authorizationPayloadHash.isBlank()) {
        throw new Web3InvalidInputException("authorizationPayloadHash is required for EIP7702");
      }
      if (executionDigest == null || executionDigest.isBlank()) {
        throw new Web3InvalidInputException("executionDigest is required for EIP7702");
      }
      return;
    }

    if (unsignedTxSnapshot == null) {
      throw new Web3InvalidInputException("unsignedTxSnapshot is required for EIP1559");
    }
    if (unsignedTxFingerprint == null || unsignedTxFingerprint.isBlank()) {
      throw new Web3InvalidInputException("unsignedTxFingerprint is required for EIP1559");
    }
  }
}
