package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CancelQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelQuestionRewardIntentService implements CancelQuestionRewardIntentUseCase {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  @Transactional
  public CancelQuestionRewardIntentResult execute(CancelQuestionRewardIntentCommand command) {
    validate(command);

    QuestionRewardIntent existing =
        questionRewardIntentPersistencePort.findForUpdateByPostId(command.postId()).orElse(null);
    if (existing == null) {
      return new CancelQuestionRewardIntentResult(command.postId(), null, false, false);
    }

    if (existing.isStaleCancelRequest(command.acceptedCommentId())) {
      return new CancelQuestionRewardIntentResult(
          existing.getPostId(), existing.getStatus(), true, false);
    }

    if (existing.cannotCancel()) {
      return new CancelQuestionRewardIntentResult(
          existing.getPostId(), existing.getStatus(), true, false);
    }

    QuestionRewardIntent saved = questionRewardIntentPersistencePort.update(existing.cancel());
    return new CancelQuestionRewardIntentResult(saved.getPostId(), saved.getStatus(), true, true);
  }

  private void validate(CancelQuestionRewardIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }
}
