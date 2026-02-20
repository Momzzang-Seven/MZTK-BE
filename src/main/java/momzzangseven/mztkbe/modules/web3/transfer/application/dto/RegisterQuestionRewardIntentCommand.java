package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Command for registering/updating QUESTION_REWARD intent from acceptance domain.
 *
 * <p>Designed to be called by other domain modules (event handler or direct service call) before
 * user-facing prepare/submit.
 */
public record RegisterQuestionRewardIntentCommand(
    Long postId, Long acceptedCommentId, Long fromUserId, Long toUserId, BigInteger amountWei) {

  public void validate() {
    if (postId == null || postId <= 0) {
      throw new Web3InvalidInputException("postId must be positive");
    }
    if (acceptedCommentId == null || acceptedCommentId <= 0) {
      throw new Web3InvalidInputException("acceptedCommentId must be positive");
    }
    if (fromUserId == null || fromUserId <= 0) {
      throw new Web3InvalidInputException("fromUserId must be positive");
    }
    if (toUserId == null || toUserId <= 0) {
      throw new Web3InvalidInputException("toUserId must be positive");
    }
    if (amountWei == null || amountWei.signum() <= 0) {
      throw new Web3InvalidInputException("amountWei must be > 0");
    }
  }

  public String referenceId() {
    return String.valueOf(postId);
  }
}
