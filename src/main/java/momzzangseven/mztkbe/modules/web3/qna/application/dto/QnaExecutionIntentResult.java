package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;

public record QnaExecutionIntentResult(
    Resource resource,
    String actionType,
    ExecutionIntent executionIntent,
    Execution execution,
    SignRequestBundle signRequest,
    boolean existing,
    SignatureMeta signatureMeta) {

  public QnaExecutionIntentResult {
    if (resource == null) {
      throw new Web3InvalidInputException("resource is required");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (executionIntent == null) {
      throw new Web3InvalidInputException("executionIntent is required");
    }
    if (execution == null) {
      throw new Web3InvalidInputException("execution is required");
    }
  }

  /**
   * Backward-compatible 6-arg constructor that leaves {@code signatureMeta} null. Used by admin
   * paths and legacy test fixtures that don't carry server-sig info.
   */
  public QnaExecutionIntentResult(
      Resource resource,
      String actionType,
      ExecutionIntent executionIntent,
      Execution execution,
      SignRequestBundle signRequest,
      boolean existing) {
    this(resource, actionType, executionIntent, execution, signRequest, existing, null);
  }

  /** Backward-compatible factory used by admin path (no signature meta). */
  public static QnaExecutionIntentResult from(
      String actionType, CreateExecutionIntentResult result) {
    return from(actionType, result, null, null);
  }

  /**
   * Server-sig aware factory. If {@code signedAt} is null, {@code signatureMeta} is null. If {@code
   * signedAt} is non-null but {@code sigValidityDuration} is null, a {@link
   * Web3InvalidInputException} is thrown.
   */
  public static QnaExecutionIntentResult from(
      String actionType,
      CreateExecutionIntentResult result,
      Long signedAt,
      Integer sigValidityDuration) {
    SignatureMeta signatureMeta;
    if (signedAt == null) {
      signatureMeta = null;
    } else {
      if (sigValidityDuration == null) {
        throw new Web3InvalidInputException(
            "sigValidityDuration is required when signedAt is present");
      }
      signatureMeta = new SignatureMeta(signedAt, signedAt + sigValidityDuration);
    }
    return new QnaExecutionIntentResult(
        new Resource(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        actionType,
        new ExecutionIntent(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new Execution(result.mode().name(), result.signCount()),
        result.signRequest(),
        result.existing(),
        signatureMeta);
  }

  public record Resource(String type, String id, String status) {

    public Resource {
      if (type == null || type.isBlank()) {
        throw new Web3InvalidInputException("resource.type is required");
      }
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("resource.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new Web3InvalidInputException("resource.status is required");
      }
    }
  }

  /**
   * Execution intent metadata.
   *
   * <p>{@code expiresAt} is the BE-side deadline by which the user must submit their user-sign to
   * the BE. <b>Type: {@link LocalDateTime} in KST</b> — the BE wall-clock, not an epoch second and
   * not UTC. Past this point {@link
   * momzzangseven.mztkbe.modules.web3.execution.application.service.TransactionalExecuteExecutionIntentDelegate}
   * refuses to broadcast and terminates the intent with {@code EXECUTION_INTENT_EXPIRED}. It is
   * independent from {@link SignatureMeta#signatureExpiresAt()}, which applies at a different phase
   * (on-chain mining) and uses a different time domain (epoch seconds on the contract clock). FE
   * surfaces this value as the user-facing countdown for signature submission.
   */
  public record ExecutionIntent(String id, String status, LocalDateTime expiresAt) {

    public ExecutionIntent {
      if (id == null || id.isBlank()) {
        throw new Web3InvalidInputException("executionIntent.id is required");
      }
      if (status == null || status.isBlank()) {
        throw new Web3InvalidInputException("executionIntent.status is required");
      }
      if (expiresAt == null) {
        throw new Web3InvalidInputException("executionIntent.expiresAt is required");
      }
    }
  }

  public record Execution(String mode, int signCount) {

    public Execution {
      if (mode == null || mode.isBlank()) {
        throw new Web3InvalidInputException("execution.mode is required");
      }
      if (signCount <= 0) {
        throw new Web3InvalidInputException("execution.signCount must be positive");
      }
    }
  }

  /**
   * Server-sig metadata surfaced to the API client. Both fields must be either both null (admin /
   * absent) or both non-null (server-sig). Mixed states are rejected.
   *
   * <p>Both fields use <b>epoch seconds in the smart-contract clock domain</b> (raw {@code Long},
   * not {@link LocalDateTime}). They are deliberately asymmetric with {@link
   * ExecutionIntent#expiresAt()}, which uses BE wall-clock {@link LocalDateTime} in KST — the two
   * values apply at different phases of the flow and live in different time domains; do not unify
   * them.
   *
   * <p>{@code signedAt} is the epoch second embedded into the on-chain calldata via the 9-arg
   * `createQuestion` overload. {@code signatureExpiresAt = signedAt + sigValidityDuration} (default
   * 900s) is the on-chain mining deadline: the broadcast tx must be included in a block before this
   * epoch second or {@code _verifyServerSig} reverts with {@code SignatureExpired}. FE typically
   * surfaces {@link ExecutionIntent#expiresAt()} as the user-facing countdown and uses {@code
   * signatureExpiresAt} only to detect mining-stage delays after broadcast.
   */
  public record SignatureMeta(Long signedAt, Long signatureExpiresAt) {

    public SignatureMeta {
      boolean signedAtAbsent = signedAt == null;
      boolean expiresAtAbsent = signatureExpiresAt == null;
      if (signedAtAbsent != expiresAtAbsent) {
        throw new Web3InvalidInputException(
            "signatureMeta requires both signedAt and signatureExpiresAt to be null or non-null");
      }
      if (!signedAtAbsent) {
        if (signedAt < 0) {
          throw new Web3InvalidInputException("signedAt must be >= 0");
        }
        if (signatureExpiresAt <= signedAt) {
          throw new Web3InvalidInputException(
              "signatureExpiresAt must be strictly greater than signedAt");
        }
      }
    }
  }
}
