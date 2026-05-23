package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

public record MarketplaceAdminEscrowReviewView(
    Long reservationId,
    boolean processable,
    String baseBlockingCode,
    String blockingReason,
    String reservationStatus,
    String escrowStatus,
    Participant buyer,
    Participant trainer,
    Token token,
    LocalDateTime reviewedAt,
    LocalDateTime chainCheckedAt,
    Long reservationVersion,
    String adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    String txHash,
    Authority authority,
    Attempt activeExecution,
    Attempt lastAttempt,
    List<ValidationItem> baseValidationItems,
    List<ReasonOption> reasonOptions) {

  public MarketplaceAdminEscrowReviewView {
    baseValidationItems =
        baseValidationItems == null ? List.of() : List.copyOf(baseValidationItems);
    reasonOptions = reasonOptions == null ? List.of() : List.copyOf(reasonOptions);
  }

  public record Participant(Long userId, String walletAddress) {}

  public record Token(String tokenAddress, BigInteger amountBaseUnits, String symbol) {}

  public record Authority(
      boolean requiresUserSignature,
      String authorityModel,
      boolean serverSignerAvailable,
      String serverSignerAddress,
      boolean relayerRegistered,
      String relayerRegistrationStatus,
      boolean canEarlySettle,
      boolean canManualRefund) {}

  public record Attempt(
      Long actionStateId,
      String attemptStatus,
      String failureStage,
      String executionIntentId,
      String executionStatus,
      String adminExecutionPhase,
      String txHash,
      String failureReason,
      String errorCode,
      String evidenceErrorCode,
      Boolean retryable,
      LocalDateTime finishedAt) {}

  public record ValidationItem(String code, String severity, String message, boolean blocking) {}

  public record ReasonOption(
      String reasonCode,
      boolean processable,
      String blockingCode,
      boolean requiresConfirmation,
      String confirmationType,
      String requiredAuthority,
      boolean authoritySatisfied,
      String displayCode,
      ResultPreview resultPreview,
      List<ValidationItem> validationItems) {

    public ReasonOption {
      validationItems = validationItems == null ? List.of() : List.copyOf(validationItems);
    }
  }

  public record ResultPreview(
      String targetReservationStatus,
      String targetEscrowStatus,
      String resolvedBy,
      String terminalReasonCode) {}
}
