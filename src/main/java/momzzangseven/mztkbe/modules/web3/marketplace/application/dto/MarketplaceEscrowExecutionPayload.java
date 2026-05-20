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
    String rootIdempotencyKey) {

  public MarketplaceEscrowExecutionPayload {
    if (payloadVersion == null) {
      payloadVersion = 1;
    }
    if (payloadVersion != 1) {
      throw new Web3InvalidInputException("unsupported marketplace payloadVersion");
    }
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
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
        null);
  }

  public MarketplaceEscrowExecutionPayload idempotencyView() {
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
        rootIdempotencyKey);
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
}
