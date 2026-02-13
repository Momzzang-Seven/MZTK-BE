package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;

public record PrepareTokenTransferCommand(
    Long userId,
    TokenTransferReferenceType referenceType,
    String referenceId,
    Long toUserId,
    BigInteger amountWei) {

  private static final int MAX_REFERENCE_ID_LENGTH = 100;
  private static final BigInteger MAX_TRANSFER_WEI =
      BigInteger.valueOf(5_000L).multiply(BigInteger.TEN.pow(18));

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (referenceType == null) {
      throw new Web3InvalidInputException("referenceType is required");
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
    if (amountWei.compareTo(MAX_TRANSFER_WEI) > 0) {
      throw new Web3InvalidInputException("amountWei exceeds max transfer limit");
    }
    if (referenceType == TokenTransferReferenceType.USER_TO_USER
        && (toUserId == null || toUserId <= 0)) {
      throw new Web3InvalidInputException("toUserId is required for USER_TO_USER");
    }
  }
}
