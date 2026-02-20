package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;

public record PrepareTokenTransferCommand(
    Long userId,
    DomainReferenceType domainType,
    String referenceId,
    Long toUserId,
    BigInteger amountWei) {

  private static final int MAX_REFERENCE_ID_LENGTH = 100;

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (domainType == null) {
      throw new Web3InvalidInputException("domainType is required");
    }
    if (referenceId == null || referenceId.isBlank()) {
      throw new Web3InvalidInputException("referenceId is required");
    }
    if (referenceId.length() > MAX_REFERENCE_ID_LENGTH) {
      throw new Web3InvalidInputException("referenceId length must be <= 100");
    }
    if (amountWei == null || amountWei.signum() <= 0) {
      throw new Web3InvalidInputException("amountWei must be > 0");
    }
    if (toUserId == null || toUserId <= 0) {
      throw new Web3InvalidInputException("toUserId must be positive");
    }
  }
}
