package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RegisterQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRewardIntentRequestedEventHandler {

  private final RegisterQuestionRewardIntentUseCase registerQuestionRewardIntentUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handle(QuestionRewardIntentRequestedEvent event) {
    registerQuestionRewardIntentUseCase.execute(
        new RegisterQuestionRewardIntentCommand(
            event.postId(),
            event.acceptedCommentId(),
            event.fromUserId(),
            event.toUserId(),
            event.amountWei()));
    log.info(
        "QUESTION_REWARD intent registered from event: postId={}, acceptedCommentId={}, fromUserId={}, toUserId={}",
        event.postId(),
        event.acceptedCommentId(),
        event.fromUserId(),
        event.toUserId());
  }
}
