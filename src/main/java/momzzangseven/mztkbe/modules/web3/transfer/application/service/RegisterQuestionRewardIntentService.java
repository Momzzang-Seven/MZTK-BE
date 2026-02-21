package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RegisterQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model.QuestionRewardIntentRecord;
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

    QuestionRewardIntentRecord existing =
        questionRewardIntentPersistencePort.findForUpdateByPostId(command.postId()).orElse(null);
    if (existing == null) {
      QuestionRewardIntentRecord created =
          questionRewardIntentPersistencePort.save(
              QuestionRewardIntentRecord.builder()
                  .postId(command.postId())
                  .acceptedCommentId(command.acceptedCommentId())
                  .fromUserId(command.fromUserId())
                  .toUserId(command.toUserId())
                  .amountWei(command.amountWei())
                  .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
                  .build());

      return new RegisterQuestionRewardIntentResult(created.getPostId(), created.getStatus(), true);
    }

    if (existing.getStatus() == QuestionRewardIntentStatus.SUBMITTED
        || existing.getStatus() == QuestionRewardIntentStatus.SUCCEEDED) {
      if (isSamePayload(existing, command)) {
        return new RegisterQuestionRewardIntentResult(
            existing.getPostId(), existing.getStatus(), false);
      }
      throw new Web3InvalidInputException(
          "question reward intent is already in-flight/completed and cannot be changed");
    }

    existing.setAcceptedCommentId(command.acceptedCommentId());
    existing.setFromUserId(command.fromUserId());
    existing.setToUserId(command.toUserId());
    existing.setAmountWei(command.amountWei());
    existing.setStatus(QuestionRewardIntentStatus.PREPARE_REQUIRED);

    QuestionRewardIntentRecord saved = questionRewardIntentPersistencePort.save(existing);
    return new RegisterQuestionRewardIntentResult(saved.getPostId(), saved.getStatus(), false);
  }

  private void validate(RegisterQuestionRewardIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
  }

  private boolean isSamePayload(
      QuestionRewardIntentRecord existing, RegisterQuestionRewardIntentCommand command) {
    return Objects.equals(existing.getAcceptedCommentId(), command.acceptedCommentId())
        && Objects.equals(existing.getFromUserId(), command.fromUserId())
        && Objects.equals(existing.getToUserId(), command.toUserId())
        && existing.getAmountWei() != null
        && existing.getAmountWei().compareTo(command.amountWei()) == 0;
  }
}
