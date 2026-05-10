package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionIntentStatus;

/**
 * Lightweight qna-owned view of the latest shared execution intent state.
 *
 * <p>The qna module uses this to guard conflicting mutations and to determine whether a local
 * question/answer can recreate its missing on-chain create intent.
 */
public record QnaExecutionIntentStateView(
    String executionIntentId,
    QnaExecutionActionType actionType,
    QnaExecutionIntentStatus status,
    String failureReason) {

  public QnaExecutionIntentStateView(
      String executionIntentId,
      QnaExecutionActionType actionType,
      QnaExecutionIntentStatus status) {
    this(executionIntentId, actionType, status, null);
  }

  public boolean isActive() {
    return status != null && status.isActive();
  }

  public boolean isTerminal() {
    return status != null && status.isTerminal();
  }

  public boolean matchesAction(QnaExecutionActionType candidate) {
    return actionType == candidate;
  }
}
