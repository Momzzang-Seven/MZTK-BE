package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAllowanceStrategy;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Marketplace-owned request for preparing a user Web3 execution from a reservation snapshot. */
public record MarketplaceEscrowExecutionRequest(
    MarketplaceExecutionActionType actionType,
    Long reservationId,
    String resourceId,
    String orderId,
    String orderKey,
    MarketplaceActorType actorType,
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
    Integer bookedPriceAmountKrw,
    LocalDateTime sessionEndAt,
    Long expectedContractDeadlineEpochSeconds,
    Long contractDeadlineEpochSeconds,
    String pendingAttemptToken,
    String targetTerminalStatus,
    Long escrowId,
    Long actionStateId,
    String rootIdempotencyKey) {

  public MarketplaceEscrowExecutionRequest {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (!actionType.isUserAction()) {
      throw new Web3InvalidInputException("user marketplace request supports only user actions");
    }
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
    if (!String.valueOf(reservationId).equals(resourceId)) {
      throw new Web3InvalidInputException("resourceId must be the reservation ID string");
    }
    requireUuid(orderId, "orderId");
    requireText(orderKey, "orderKey");
    if (actorType == null) {
      actorType = defaultActor(actionType, requesterUserId, buyerUserId, trainerUserId);
    }
    authorityUserId = authorityUserId == null ? requesterUserId : authorityUserId;
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
      allowanceStrategy = MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE;
    }
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new Web3InvalidInputException("bookedPriceAmountKrw must be positive");
    }
    if (sessionEndAt == null) {
      throw new Web3InvalidInputException("sessionEndAt is required");
    }
    if (escrowId != null && escrowId <= 0) {
      throw new Web3InvalidInputException("escrowId must be positive");
    }
    if (actionStateId != null && actionStateId <= 0) {
      throw new Web3InvalidInputException("actionStateId must be positive");
    }
  }

  public MarketplaceEscrowExecutionRequest(
      MarketplaceExecutionActionType actionType,
      Long reservationId,
      String resourceId,
      String orderId,
      String orderKey,
      MarketplaceActorType actorType,
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
      Integer bookedPriceAmountKrw,
      LocalDateTime sessionEndAt,
      Long expectedContractDeadlineEpochSeconds,
      Long contractDeadlineEpochSeconds,
      String pendingAttemptToken,
      String targetTerminalStatus) {
    this(
        actionType,
        reservationId,
        resourceId,
        orderId,
        orderKey,
        actorType,
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
        bookedPriceAmountKrw,
        sessionEndAt,
        expectedContractDeadlineEpochSeconds,
        contractDeadlineEpochSeconds,
        pendingAttemptToken,
        targetTerminalStatus,
        null,
        null,
        null);
  }

  public MarketplaceEscrowExecutionRequest(
      MarketplaceExecutionActionType actionType,
      Long reservationId,
      String resourceId,
      String orderId,
      Long requesterUserId,
      Long buyerUserId,
      Long trainerUserId,
      Long reservationVersion,
      String buyerWalletAddress,
      String trainerWalletAddress,
      Integer bookedPriceAmountKrw,
      LocalDateTime sessionEndAt) {
    this(
        actionType,
        reservationId,
        resourceId,
        orderId,
        "0x0000000000000000000000000000000000000000000000000000000000000001",
        defaultActor(actionType, requesterUserId, buyerUserId, trainerUserId),
        requesterUserId,
        requesterUserId,
        requesterUserId != null && requesterUserId.equals(buyerUserId)
            ? trainerUserId
            : buyerUserId,
        buyerUserId,
        trainerUserId,
        reservationVersion,
        null,
        null,
        buyerWalletAddress,
        trainerWalletAddress,
        "0x0000000000000000000000000000000000000000",
        BigInteger.valueOf(bookedPriceAmountKrw),
        MarketplaceAllowanceStrategy.PRE_EXISTING_ALLOWANCE,
        bookedPriceAmountKrw,
        sessionEndAt,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static MarketplaceActorType defaultActor(
      MarketplaceExecutionActionType actionType,
      Long requesterUserId,
      Long buyerUserId,
      Long trainerUserId) {
    if (actionType == MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL
        && requesterUserId != null
        && requesterUserId.equals(trainerUserId)) {
      return MarketplaceActorType.TRAINER;
    }
    return MarketplaceActorType.BUYER;
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private static void requireUuid(String value, String fieldName) {
    requireText(value, fieldName);
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new Web3InvalidInputException(fieldName + " must be a UUID");
    }
  }
}
