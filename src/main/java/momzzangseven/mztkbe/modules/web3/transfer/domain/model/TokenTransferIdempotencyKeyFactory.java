package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Policy-fixed idempotency key format for EIP-7702 transfer flow. */
public final class TokenTransferIdempotencyKeyFactory {

  private TokenTransferIdempotencyKeyFactory() {}

  public static String create(
      DomainReferenceType domainType, Long fromUserId, String referenceId) {
    if (domainType == null) {
      throw new Web3InvalidInputException("domainType is required");
    }
    if (fromUserId == null || fromUserId <= 0) {
      throw new Web3InvalidInputException("fromUserId must be positive");
    }
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }

    return "domain:" + domainType.name() + ":" + referenceId + ":" + fromUserId;
  }

  public static DomainReferenceType parseDomainType(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    String[] tokens = idempotencyKey.split(":");
    if (tokens.length < 4 || !"domain".equals(tokens[0])) {
      return null;
    }
    try {
      return DomainReferenceType.valueOf(tokens[1]);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
