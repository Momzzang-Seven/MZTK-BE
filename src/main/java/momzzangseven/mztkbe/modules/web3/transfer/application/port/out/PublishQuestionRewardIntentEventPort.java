package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentCanceledEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;

public interface PublishQuestionRewardIntentEventPort {

  void publishRequested(QuestionRewardIntentRequestedEvent event);

  void publishCanceled(QuestionRewardIntentCanceledEvent event);
}
