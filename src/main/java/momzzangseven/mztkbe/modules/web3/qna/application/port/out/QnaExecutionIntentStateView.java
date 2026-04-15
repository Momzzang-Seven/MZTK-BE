package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;

/**
 * Lightweight qna-owned view of the latest shared execution intent state.
 *
 * <p>The qna module uses this to guard conflicting mutations and to determine whether a local
 * question/answer can recreate its missing on-chain create intent.
 */
public record QnaExecutionIntentStateView(
    String executionIntentId, QnaExecutionActionType actionType, ExecutionIntentStatus status) {

  public boolean isActive() {
    return status == ExecutionIntentStatus.AWAITING_SIGNATURE
        || status == ExecutionIntentStatus.SIGNED
        || status == ExecutionIntentStatus.PENDING_ONCHAIN;
  }

  public boolean isTerminal() {
    return status != null && status.isTerminal();
  }

  public boolean matchesAction(QnaExecutionActionType candidate) {
    return actionType == candidate;
  }
}
