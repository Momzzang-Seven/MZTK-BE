package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;

public record CancelQuestionRewardIntentResult(
    Long postId, QuestionRewardIntentStatus status, boolean found, boolean changed) {}
