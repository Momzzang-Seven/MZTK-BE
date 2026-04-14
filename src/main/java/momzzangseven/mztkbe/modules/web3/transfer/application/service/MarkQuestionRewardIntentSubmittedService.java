package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.MarkQuestionRewardIntentSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.MarkQuestionRewardIntentSubmittedUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkQuestionRewardIntentSubmittedService
    implements MarkQuestionRewardIntentSubmittedUseCase {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  @Transactional
  public void execute(MarkQuestionRewardIntentSubmittedCommand command) {
    questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
        command.postId(),
        QuestionRewardIntentStatus.SUBMITTED,
        EnumSet.of(QuestionRewardIntentStatus.PREPARE_REQUIRED));
  }
}
