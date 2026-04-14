package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;

/**
 * Integration bridge for acceptance domain.
 *
 * <p>Acceptance domain should call this in the same transaction where accepted-state is persisted.
 * Actual intent upsert/cancel runs via AFTER_COMMIT listeners.
 */
public interface PublishQuestionRewardIntentEventUseCase {

  void publishRegisterRequested(RegisterQuestionRewardIntentCommand command);

  void publishCancelRequested(CancelQuestionRewardIntentCommand command);
}
