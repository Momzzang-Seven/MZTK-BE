package momzzangseven.mztkbe.modules.web3.marketplace.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Builds deterministic root idempotency keys for marketplace user execution intents. */
public final class MarketplaceEscrowIdempotencyKeyFactory {

  private MarketplaceEscrowIdempotencyKeyFactory() {}

  public static String create(
      MarketplaceExecutionActionType actionType, Long requesterUserId, Long reservationId) {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
    return "marketplace:"
        + actionType.name().toLowerCase()
        + ":"
        + requesterUserId
        + ":"
        + reservationId;
  }
}
