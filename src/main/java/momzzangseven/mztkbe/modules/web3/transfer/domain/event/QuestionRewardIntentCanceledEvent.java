package momzzangseven.mztkbe.modules.web3.transfer.domain.event;

/** Event contract for canceling QUESTION_REWARD intent after acceptance revert/change. */
public record QuestionRewardIntentCanceledEvent(Long postId, Long acceptedCommentId) {}
