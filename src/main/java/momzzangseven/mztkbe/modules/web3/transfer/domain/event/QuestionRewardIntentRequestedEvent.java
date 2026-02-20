package momzzangseven.mztkbe.modules.web3.transfer.domain.event;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;

/**
 * Event contract for decoupled QUESTION_REWARD intent registration.
 *
 * <p>Publish from acceptance domain after commit.
 */
public record QuestionRewardIntentRequestedEvent(
    Long postId,
    Long acceptedCommentId,
    Long fromUserId,
    Long toUserId,
    BigInteger amountWei) {

  public RegisterQuestionRewardIntentCommand toCommand() {
    return new RegisterQuestionRewardIntentCommand(
        postId, acceptedCommentId, fromUserId, toUserId, amountWei);
  }
}
