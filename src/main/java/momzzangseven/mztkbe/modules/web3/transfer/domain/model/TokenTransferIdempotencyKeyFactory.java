package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Policy-fixed idempotency key format for EIP-7702 transfer flow. */
public final class TokenTransferIdempotencyKeyFactory {

  private TokenTransferIdempotencyKeyFactory() {}

  public static String create(
      TokenTransferReferenceType referenceType,
      Long fromUserId,
      Long toUserId,
      String referenceId) {
    if (referenceType == null) {
      throw new Web3InvalidInputException("referenceType is required");
    }
    if (fromUserId == null || fromUserId <= 0) {
      throw new Web3InvalidInputException("fromUserId must be positive");
    }
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }

    return switch (referenceType) {
      case USER_TO_USER -> {
        if (toUserId == null || toUserId <= 0) {
          throw new Web3InvalidInputException("toUserId must be positive for USER_TO_USER");
        }
        yield "u2u:" + fromUserId + ":" + toUserId + ":" + referenceId;
      }
      case USER_TO_SERVER -> "u2s:" + fromUserId + ":" + referenceId;
    };
  }
}
