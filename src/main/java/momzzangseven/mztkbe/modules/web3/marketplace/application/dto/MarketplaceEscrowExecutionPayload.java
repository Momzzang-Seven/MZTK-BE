package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Immutable marketplace payload snapshot persisted on the shared execution intent. */
public record MarketplaceEscrowExecutionPayload(
    MarketplaceExecutionActionType actionType,
    MarketplaceActorType actorType,
    Long reservationId,
    String resourceId,
    String orderId,
    String orderKey,
    Long authorityUserId,
    Long requesterUserId,
    Long counterpartyUserId,
    Long buyerUserId,
    Long trainerUserId,
    Long reservationVersion,
    String expectedReservationStatus,
    String expectedEscrowStatus,
    String buyerWalletAddress,
    String trainerWalletAddress,
    String tokenAddress,
    BigInteger priceBaseUnits,
    MarketplaceAllowanceStrategy allowanceStrategy,
    LocalDateTime sessionEndAt,
    Long expectedContractDeadlineEpochSeconds,
    Long contractDeadlineEpochSeconds,
    String pendingAttemptToken,
    String targetTerminalStatus,
    String callTarget,
    String callData,
    MarketplaceTokenMovement tokenMovement,
    Long signedAt,
    String signatureHex,
    Integer payloadVersion,
    Long escrowId,
    Long actionStateId,
    String rootIdempotencyKey,
    MarketplaceAdminExecutionProvenanceActor adminProvenanceActor,
    String requestSource,
    Long operatorUserId,
    String schedulerRunId,
    String reasonCode,
    String memo) {

  public MarketplaceEscrowExecutionPayload {
    if (payloadVersion == null) {
      payloadVersion = 1;
    }
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (requestSource == null || requestSource.isBlank()) {
      requestSource = actionType.isUserAction() ? "USER" : null;
    }
    memo = trimToNull(memo);
    if (actionType.isUserAction()) {
      if (payloadVersion != 1) {
        throw new Web3InvalidInputException("user marketplace payloadVersion must be 1");
      }
      validateUserPayload(
          actorType,
          reservationId,
          resourceId,
          orderId,
          orderKey,
          authorityUserId,
          requesterUserId,
          buyerUserId,
          trainerUserId,
          reservationVersion,
          buyerWalletAddress,
          trainerWalletAddress,
          tokenAddress,
          priceBaseUnits,
          allowanceStrategy,
          sessionEndAt,
          tokenMovement,
          escrowId,
          actionStateId);
    } else {
      validateAdminPayload(
          payloadVersion,
          adminProvenanceActor,
          requestSource,
          reasonCode,
          reservationId,
          resourceId,
          orderId,
          orderKey,
          requesterUserId,
          counterpartyUserId,
          buyerUserId,
          trainerUserId,
          reservationVersion,
          expectedReservationStatus,
          expectedEscrowStatus,
          buyerWalletAddress,
          trainerWalletAddress,
          tokenAddress,
          priceBaseUnits,
          callTarget,
          callData,
          tokenMovement,
          escrowId,
          actionStateId,
          rootIdempotencyKey);
    }
  }

  private static void validateUserPayload(
      MarketplaceActorType actorType,
      Long reservationId,
      String resourceId,
      String orderId,
      String orderKey,
      Long authorityUserId,
      Long requesterUserId,
      Long buyerUserId,
      Long trainerUserId,
      Long reservationVersion,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      BigInteger priceBaseUnits,
      MarketplaceAllowanceStrategy allowanceStrategy,
      LocalDateTime sessionEndAt,
      MarketplaceTokenMovement tokenMovement,
      Long escrowId,
      Long actionStateId) {
    if (actorType == null) {
      throw new Web3InvalidInputException("actorType is required");
    }
    requirePositive(reservationId, "reservationId");
    requireText(resourceId, "resourceId");
    requireText(orderId, "orderId");
    requireText(orderKey, "orderKey");
    requirePositive(authorityUserId, "authorityUserId");
    requirePositive(requesterUserId, "requesterUserId");
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    if (reservationVersion == null || reservationVersion < 0) {
      throw new Web3InvalidInputException("reservationVersion must be >= 0");
    }
    requireText(buyerWalletAddress, "buyerWalletAddress");
    requireText(trainerWalletAddress, "trainerWalletAddress");
    requireText(tokenAddress, "tokenAddress");
    if (priceBaseUnits == null || priceBaseUnits.signum() <= 0) {
      throw new Web3InvalidInputException("priceBaseUnits must be positive");
    }
    if (allowanceStrategy == null) {
      throw new Web3InvalidInputException("allowanceStrategy is required");
    }
    if (sessionEndAt == null) {
      throw new Web3InvalidInputException("sessionEndAt is required");
    }
    if (tokenMovement == null) {
      throw new Web3InvalidInputException("tokenMovement is required");
    }
    if (escrowId != null && escrowId <= 0) {
      throw new Web3InvalidInputException("escrowId must be positive");
    }
    if (actionStateId != null && actionStateId <= 0) {
      throw new Web3InvalidInputException("actionStateId must be positive");
    }
  }

  private static void validateAdminPayload(
      Integer payloadVersion,
      MarketplaceAdminExecutionProvenanceActor adminProvenanceActor,
      String requestSource,
      String reasonCode,
      Long reservationId,
      String resourceId,
      String orderId,
      String orderKey,
      Long requesterUserId,
      Long counterpartyUserId,
      Long buyerUserId,
      Long trainerUserId,
      Long reservationVersion,
      String expectedReservationStatus,
      String expectedEscrowStatus,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      BigInteger priceBaseUnits,
      String callTarget,
      String callData,
      MarketplaceTokenMovement tokenMovement,
      Long escrowId,
      Long actionStateId,
      String rootIdempotencyKey) {
    if (payloadVersion != 2) {
      throw new Web3InvalidInputException("admin marketplace payloadVersion must be 2");
    }
    if (adminProvenanceActor == null) {
      throw new Web3InvalidInputException("adminProvenanceActor is required");
    }
    if (!"MANUAL_ADMIN".equals(requestSource) && !"SCHEDULER".equals(requestSource)) {
      throw new Web3InvalidInputException("admin requestSource is required");
    }
    requireText(reasonCode, "reasonCode");
    requirePositive(reservationId, "reservationId");
    requireText(resourceId, "resourceId");
    requireText(orderId, "orderId");
    requireText(orderKey, "orderKey");
    requirePositive(requesterUserId, "requesterUserId");
    requirePositive(counterpartyUserId, "counterpartyUserId");
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    if (reservationVersion == null || reservationVersion < 0) {
      throw new Web3InvalidInputException("reservationVersion must be >= 0");
    }
    requireText(expectedReservationStatus, "expectedReservationStatus");
    requireText(expectedEscrowStatus, "expectedEscrowStatus");
    requireText(buyerWalletAddress, "buyerWalletAddress");
    requireText(trainerWalletAddress, "trainerWalletAddress");
    requireText(tokenAddress, "tokenAddress");
    if (priceBaseUnits == null || priceBaseUnits.signum() <= 0) {
      throw new Web3InvalidInputException("priceBaseUnits must be positive");
    }
    requireText(callTarget, "callTarget");
    requireText(callData, "callData");
    if (tokenMovement == null) {
      throw new Web3InvalidInputException("tokenMovement is required");
    }
    requirePositive(escrowId, "escrowId");
    requirePositive(actionStateId, "actionStateId");
    requireText(rootIdempotencyKey, "rootIdempotencyKey");
  }

  public MarketplaceEscrowExecutionPayload(
      MarketplaceExecutionActionType actionType,
      MarketplaceActorType actorType,
      Long reservationId,
      String resourceId,
      String orderId,
      String orderKey,
      Long authorityUserId,
      Long requesterUserId,
      Long counterpartyUserId,
      Long buyerUserId,
      Long trainerUserId,
      Long reservationVersion,
      String expectedReservationStatus,
      String expectedEscrowStatus,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      BigInteger priceBaseUnits,
      MarketplaceAllowanceStrategy allowanceStrategy,
      LocalDateTime sessionEndAt,
      Long expectedContractDeadlineEpochSeconds,
      Long contractDeadlineEpochSeconds,
      String pendingAttemptToken,
      String targetTerminalStatus,
      String callTarget,
      String callData,
      MarketplaceTokenMovement tokenMovement,
      Long signedAt,
      String signatureHex) {
    this(
        actionType,
        actorType,
        reservationId,
        resourceId,
        orderId,
        orderKey,
        authorityUserId,
        requesterUserId,
        counterpartyUserId,
        buyerUserId,
        trainerUserId,
        reservationVersion,
        expectedReservationStatus,
        expectedEscrowStatus,
        buyerWalletAddress,
        trainerWalletAddress,
        tokenAddress,
        priceBaseUnits,
        allowanceStrategy,
        sessionEndAt,
        expectedContractDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        pendingAttemptToken,
        targetTerminalStatus,
        callTarget,
        callData,
        tokenMovement,
        signedAt,
        signatureHex,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public MarketplaceEscrowExecutionPayload(
      MarketplaceExecutionActionType actionType,
      MarketplaceActorType actorType,
      Long reservationId,
      String resourceId,
      String orderId,
      String orderKey,
      Long authorityUserId,
      Long requesterUserId,
      Long counterpartyUserId,
      Long buyerUserId,
      Long trainerUserId,
      Long reservationVersion,
      String expectedReservationStatus,
      String expectedEscrowStatus,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      BigInteger priceBaseUnits,
      MarketplaceAllowanceStrategy allowanceStrategy,
      LocalDateTime sessionEndAt,
      Long expectedContractDeadlineEpochSeconds,
      Long contractDeadlineEpochSeconds,
      String pendingAttemptToken,
      String targetTerminalStatus,
      String callTarget,
      String callData,
      MarketplaceTokenMovement tokenMovement,
      Long signedAt,
      String signatureHex,
      Integer payloadVersion,
      Long escrowId,
      Long actionStateId,
      String rootIdempotencyKey) {
    this(
        actionType,
        actorType,
        reservationId,
        resourceId,
        orderId,
        orderKey,
        authorityUserId,
        requesterUserId,
        counterpartyUserId,
        buyerUserId,
        trainerUserId,
        reservationVersion,
        expectedReservationStatus,
        expectedEscrowStatus,
        buyerWalletAddress,
        trainerWalletAddress,
        tokenAddress,
        priceBaseUnits,
        allowanceStrategy,
        sessionEndAt,
        expectedContractDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        pendingAttemptToken,
        targetTerminalStatus,
        callTarget,
        callData,
        tokenMovement,
        signedAt,
        signatureHex,
        payloadVersion,
        escrowId,
        actionStateId,
        rootIdempotencyKey,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public Object idempotencyView() {
    if (actionType.isAdminAction()) {
      return new AdminIdempotencyView(
          actionType,
          reservationId,
          resourceId,
          orderId,
          orderKey,
          requestSource,
          reasonCode,
          requesterUserId,
          counterpartyUserId,
          buyerUserId,
          trainerUserId,
          expectedReservationStatus,
          expectedEscrowStatus,
          callTarget,
          callData,
          tokenMovement,
          pendingAttemptToken,
          actionStateId,
          rootIdempotencyKey);
    }
    return new MarketplaceEscrowExecutionPayload(
        actionType,
        actorType,
        reservationId,
        resourceId,
        orderId,
        orderKey,
        authorityUserId,
        requesterUserId,
        counterpartyUserId,
        buyerUserId,
        trainerUserId,
        reservationVersion,
        expectedReservationStatus,
        expectedEscrowStatus,
        buyerWalletAddress,
        trainerWalletAddress,
        tokenAddress,
        priceBaseUnits,
        allowanceStrategy,
        sessionEndAt,
        expectedContractDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        pendingAttemptToken,
        targetTerminalStatus,
        callTarget,
        null,
        tokenMovement,
        null,
        null,
        payloadVersion,
        escrowId,
        actionStateId,
        rootIdempotencyKey,
        adminProvenanceActor,
        requestSource,
        operatorUserId,
        schedulerRunId,
        reasonCode,
        memo);
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Admin execution-semantic projection used for payload hashing. */
  public record AdminIdempotencyView(
      MarketplaceExecutionActionType actionType,
      Long reservationId,
      String resourceId,
      String orderId,
      String orderKey,
      String requestSource,
      String reasonCode,
      Long requesterUserId,
      Long counterpartyUserId,
      Long buyerUserId,
      Long trainerUserId,
      String expectedReservationStatus,
      String expectedEscrowStatus,
      String callTarget,
      String callData,
      MarketplaceTokenMovement tokenMovement,
      String pendingAttemptToken,
      Long actionStateId,
      String rootIdempotencyKey) {}
}
