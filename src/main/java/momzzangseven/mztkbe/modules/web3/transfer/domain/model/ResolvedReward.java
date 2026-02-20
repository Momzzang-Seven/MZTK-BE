package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Resolved transfer target and amount loaded from domain source of truth. */
public record ResolvedReward(Long toUserId, BigInteger amountWei, Long acceptedCommentId) {

  public ResolvedReward {
    if (toUserId == null || toUserId <= 0) {
      throw new Web3InvalidInputException("resolved toUserId must be positive");
    }
    if (amountWei == null || amountWei.signum() <= 0) {
      throw new Web3InvalidInputException("resolved amountWei must be > 0");
    }
    if (acceptedCommentId != null && acceptedCommentId <= 0) {
      throw new Web3InvalidInputException("acceptedCommentId must be positive when provided");
    }
  }
}
