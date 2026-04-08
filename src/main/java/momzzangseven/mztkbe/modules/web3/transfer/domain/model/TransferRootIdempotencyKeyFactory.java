package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public final class TransferRootIdempotencyKeyFactory {

  private TransferRootIdempotencyKeyFactory() {}

  public static String create(Long requesterUserId, String clientRequestId) {
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (clientRequestId == null || clientRequestId.isBlank()) {
      throw new Web3InvalidInputException("clientRequestId is required");
    }
    if (clientRequestId.length() > 100) {
      throw new Web3InvalidInputException("clientRequestId length must be <= 100");
    }

    return "web3:TRANSFER_SEND:" + requesterUserId + ":" + clientRequestId;
  }
}
