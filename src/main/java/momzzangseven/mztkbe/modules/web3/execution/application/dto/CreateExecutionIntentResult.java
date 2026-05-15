package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

/**
 * Result contract returned after execution intent create/reuse.
 *
 * <p>{@code payloadSnapshotJson} mirrors the persisted intent's snapshot so that module-specific
 * adapters can recover stored fields (e.g. QnA's server-sig {@code signedAt}) when {@code existing}
 * is true and a new in-memory draft would otherwise overwrite them in the API response. Nullable to
 * preserve compatibility with callers that don't yet need it.
 */
public record CreateExecutionIntentResult(
    ExecutionResourceType resourceType,
    String resourceId,
    ExecutionResourceStatus resourceStatus,
    String executionIntentId,
    ExecutionIntentStatus executionIntentStatus,
    LocalDateTime expiresAt,
    long expiresAtEpochSeconds,
    ExecutionMode mode,
    int signCount,
    SignRequestBundle signRequest,
    boolean existing,
    String payloadSnapshotJson) {

  private static final ZoneId LEGACY_APP_ZONE = ZoneId.of("Asia/Seoul");

  /** Validates required create result fields before exposing API contract. */
  public CreateExecutionIntentResult {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
    if (resourceStatus == null) {
      throw new Web3InvalidInputException("resourceStatus is required");
    }
    if (executionIntentId == null || executionIntentId.isBlank()) {
      throw new Web3InvalidInputException("executionIntentId is required");
    }
    if (executionIntentStatus == null) {
      throw new Web3InvalidInputException("executionIntentStatus is required");
    }
    if (expiresAt == null) {
      throw new Web3InvalidInputException("expiresAt is required");
    }
    if (expiresAtEpochSeconds <= 0) {
      throw new Web3InvalidInputException("expiresAtEpochSeconds must be positive");
    }
    if (mode == null) {
      throw new Web3InvalidInputException("mode is required");
    }
    if (signCount <= 0) {
      throw new Web3InvalidInputException("signCount must be positive");
    }
    if (executionIntentStatus == ExecutionIntentStatus.AWAITING_SIGNATURE && signRequest == null) {
      throw new Web3InvalidInputException("signRequest is required");
    }
  }

  /**
   * Backward-compatible constructor that carries epoch seconds but leaves {@code
   * payloadSnapshotJson} null.
   */
  public CreateExecutionIntentResult(
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionResourceStatus resourceStatus,
      String executionIntentId,
      ExecutionIntentStatus executionIntentStatus,
      LocalDateTime expiresAt,
      long expiresAtEpochSeconds,
      ExecutionMode mode,
      int signCount,
      SignRequestBundle signRequest,
      boolean existing) {
    this(
        resourceType,
        resourceId,
        resourceStatus,
        executionIntentId,
        executionIntentStatus,
        expiresAt,
        expiresAtEpochSeconds,
        mode,
        signCount,
        signRequest,
        existing,
        null);
  }

  /**
   * Backward-compatible constructor that derives epoch seconds and leaves {@code
   * payloadSnapshotJson} null. Used by existing test fixtures and callers that do not consume the
   * stored snapshot.
   */
  public CreateExecutionIntentResult(
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionResourceStatus resourceStatus,
      String executionIntentId,
      ExecutionIntentStatus executionIntentStatus,
      LocalDateTime expiresAt,
      ExecutionMode mode,
      int signCount,
      SignRequestBundle signRequest,
      boolean existing) {
    this(
        resourceType,
        resourceId,
        resourceStatus,
        executionIntentId,
        executionIntentStatus,
        expiresAt,
        expiresAt == null ? 0 : expiresAt.atZone(LEGACY_APP_ZONE).toEpochSecond(),
        mode,
        signCount,
        signRequest,
        existing,
        null);
  }
}
