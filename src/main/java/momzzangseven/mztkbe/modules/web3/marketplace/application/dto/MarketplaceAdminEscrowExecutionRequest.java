package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceAdminExecutionRequestSource;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Marketplace-owned request for preparing an admin direct EIP-1559 escrow execution. */
public record MarketplaceAdminEscrowExecutionRequest(
    MarketplaceExecutionActionType actionType,
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
    Integer bookedPriceAmountKrw,
    LocalDateTime sessionEndAt,
    String pendingAttemptToken,
    String targetTerminalStatus,
    Long escrowId,
    Long actionStateId,
    MarketplaceAdminExecutionRequestSource requestSource,
    Long operatorUserId,
    String schedulerRunId,
    String reasonCode,
    String memo,
    String rootIdempotencyKey) {

  public MarketplaceAdminEscrowExecutionRequest {
    if (actionType == null || !actionType.isAdminAction()) {
      throw new Web3InvalidInputException("admin actionType is required");
    }
    requirePositive(reservationId, "reservationId");
    requireText(resourceId, "resourceId");
    if (!String.valueOf(reservationId).equals(resourceId)) {
      throw new Web3InvalidInputException("resourceId must be the reservation ID string");
    }
    requireUuid(orderId, "orderId");
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
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new Web3InvalidInputException("bookedPriceAmountKrw must be positive");
    }
    if (sessionEndAt == null) {
      throw new Web3InvalidInputException("sessionEndAt is required");
    }
    requireText(pendingAttemptToken, "pendingAttemptToken");
    requireText(targetTerminalStatus, "targetTerminalStatus");
    requirePositive(escrowId, "escrowId");
    requirePositive(actionStateId, "actionStateId");
    if (requestSource == null) {
      throw new Web3InvalidInputException("requestSource is required");
    }
    if (requestSource == MarketplaceAdminExecutionRequestSource.MANUAL_ADMIN) {
      requirePositive(operatorUserId, "operatorUserId");
      schedulerRunId = null;
    } else {
      operatorUserId = null;
      memo = null;
    }
    requireText(reasonCode, "reasonCode");
    memo = trimToNull(memo);
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

  private static void requireUuid(String value, String fieldName) {
    requireText(value, fieldName);
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new Web3InvalidInputException(fieldName + " must be a UUID");
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
