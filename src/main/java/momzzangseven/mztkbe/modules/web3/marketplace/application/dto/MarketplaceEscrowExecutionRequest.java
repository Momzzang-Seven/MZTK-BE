package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;

/** Marketplace-owned request for preparing a user Web3 execution from a reservation snapshot. */
public record MarketplaceEscrowExecutionRequest(
    MarketplaceExecutionActionType actionType,
    Long reservationId,
    String resourceId,
    String orderKey,
    Long requesterUserId,
    Long buyerUserId,
    Long trainerUserId,
    Long reservationVersion,
    String buyerWalletAddress,
    String trainerWalletAddress,
    Integer bookedPriceAmountKrw,
    LocalDateTime sessionEndAt) {

  public MarketplaceEscrowExecutionRequest {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
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
    if (orderKey == null || orderKey.isBlank()) {
      throw new Web3InvalidInputException("orderKey is required");
    }
    if (!MarketplaceEscrowIdCodec.orderKey(reservationId).equals(orderKey)) {
      throw new Web3InvalidInputException("orderKey must match reservationId");
    }
    requirePositive(requesterUserId, "requesterUserId");
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    if (reservationVersion == null || reservationVersion < 0) {
      throw new Web3InvalidInputException("reservationVersion must be >= 0");
    }
    requireText(buyerWalletAddress, "buyerWalletAddress");
    requireText(trainerWalletAddress, "trainerWalletAddress");
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new Web3InvalidInputException("bookedPriceAmountKrw must be positive");
    }
    if (sessionEndAt == null) {
      throw new Web3InvalidInputException("sessionEndAt is required");
    }
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
