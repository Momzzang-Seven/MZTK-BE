package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RegisterQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterQuestionRewardIntentService implements RegisterQuestionRewardIntentUseCase {

  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  @Transactional
  public RegisterQuestionRewardIntentResult execute(RegisterQuestionRewardIntentCommand command) {
    validate(command);

    QuestionRewardIntent existing =
        questionRewardIntentPersistencePort.findForUpdateByPostId(command.postId()).orElse(null);
    if (existing == null) {
      QuestionRewardIntent created =
          questionRewardIntentPersistencePort.create(
              QuestionRewardIntent.builder()
                  .postId(command.postId())
                  .acceptedCommentId(command.acceptedCommentId())
                  .fromUserId(command.fromUserId())
                  .toUserId(command.toUserId())
                  .amountWei(command.amountWei())
                  .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
                  .build());

      return new RegisterQuestionRewardIntentResult(created.getPostId(), created.getStatus(), true);
    }

    if (existing.isImmutableForRegister()) {
      if (existing.isSamePayload(
          command.acceptedCommentId(),
          command.fromUserId(),
          command.toUserId(),
          command.amountWei())) {
        return new RegisterQuestionRewardIntentResult(
            existing.getPostId(), existing.getStatus(), false);
      }
      throw new Web3InvalidInputException(
          "question reward intent is already in-flight/completed and cannot be changed");
    }

    QuestionRewardIntent updated =
        existing.withPrepareRequired(
            command.acceptedCommentId(),
            command.fromUserId(),
            command.toUserId(),
            command.amountWei());

    QuestionRewardIntent saved = questionRewardIntentPersistencePort.update(updated);
    return new RegisterQuestionRewardIntentResult(saved.getPostId(), saved.getStatus(), false);
  }

  private void validate(RegisterQuestionRewardIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }
}
