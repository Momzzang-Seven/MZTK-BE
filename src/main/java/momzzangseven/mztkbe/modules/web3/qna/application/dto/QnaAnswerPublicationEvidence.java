package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;

public record QnaAnswerPublicationEvidence(
    Long answerId,
    String executionIntentId,
    QnaExecutionActionType actionType,
    QnaExecutionIntentStatus status,
    String failureReason,
    boolean answerProjectionExists) {

  public boolean isConfirmed() {
    return status != null && status.isConfirmed();
  }

  public boolean isTerminalFailure() {
    return status != null && status.isTerminalFailure();
  }
}
