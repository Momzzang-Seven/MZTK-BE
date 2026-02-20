package momzzangseven.mztkbe.modules.web3.transfer.domain.event;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;

/** Event contract for canceling QUESTION_REWARD intent after acceptance revert/change. */
public record QuestionRewardIntentCanceledEvent(Long postId, Long acceptedCommentId) {

  public CancelQuestionRewardIntentCommand toCommand() {
    return new CancelQuestionRewardIntentCommand(postId, acceptedCommentId);
  }
}
