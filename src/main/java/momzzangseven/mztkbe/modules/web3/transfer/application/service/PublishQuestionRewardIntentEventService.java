package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PublishQuestionRewardIntentEventUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.PublishQuestionRewardIntentEventPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentCanceledEvent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishQuestionRewardIntentEventService
    implements PublishQuestionRewardIntentEventUseCase {

  private final PublishQuestionRewardIntentEventPort publishQuestionRewardIntentEventPort;

  @Override
  @Transactional
  public void publishRegisterRequested(RegisterQuestionRewardIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    publishQuestionRewardIntentEventPort.publishRequested(
        new QuestionRewardIntentRequestedEvent(
            command.postId(),
            command.acceptedCommentId(),
            command.fromUserId(),
            command.toUserId(),
            command.amountWei()));
  }

  @Override
  @Transactional
  public void publishCancelRequested(CancelQuestionRewardIntentCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    command.validate();
    publishQuestionRewardIntentEventPort.publishCanceled(
        new QuestionRewardIntentCanceledEvent(command.postId(), command.acceptedCommentId()));
  }
}
