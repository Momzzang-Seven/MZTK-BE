package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record CreateTransferCommand(
    Long userId, Long toUserId, String clientRequestId, BigInteger amountWei) {

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new Web3InvalidInputException("userId must be positive");
    }
    if (toUserId == null || toUserId <= 0) {
      throw new Web3InvalidInputException("toUserId must be positive");
    }
    if (clientRequestId == null || clientRequestId.isBlank()) {
      throw new Web3InvalidInputException("clientRequestId is required");
    }
    if (clientRequestId.length() > 100) {
      throw new Web3InvalidInputException("clientRequestId length must be <= 100");
    }
    if (amountWei == null || amountWei.signum() <= 0) {
      throw new Web3InvalidInputException("amountWei must be > 0");
    }
  }
}
