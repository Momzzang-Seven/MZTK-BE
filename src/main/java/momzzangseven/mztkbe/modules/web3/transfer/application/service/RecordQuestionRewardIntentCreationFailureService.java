package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RecordQuestionRewardIntentCreationFailureUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RecordQuestionRewardIntentCreationFailureService
    implements RecordQuestionRewardIntentCreationFailureUseCase {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  public void execute(Long postId, String errorCode, String errorReason) {
    if (postId == null || postId <= 0) {
      return;
    }

    questionRewardIntentPersistencePort
        .findForUpdateByPostId(postId)
        .ifPresent(
            intent ->
                questionRewardIntentPersistencePort.update(
                    intent.markExecutionIntentCreationFailed(errorCode, errorReason)));
  }
}
