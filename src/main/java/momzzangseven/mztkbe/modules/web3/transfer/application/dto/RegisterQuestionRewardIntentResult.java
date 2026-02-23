package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;

public record RegisterQuestionRewardIntentResult(
    Long postId, QuestionRewardIntentStatus status, boolean created) {}
