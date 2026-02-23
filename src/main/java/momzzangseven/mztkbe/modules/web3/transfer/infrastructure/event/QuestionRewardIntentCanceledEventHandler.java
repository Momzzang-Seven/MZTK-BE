package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CancelQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CancelQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentCanceledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionRewardIntentCanceledEventHandler {

  private final CancelQuestionRewardIntentUseCase cancelQuestionRewardIntentUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handle(QuestionRewardIntentCanceledEvent event) {
    var result =
        cancelQuestionRewardIntentUseCase.execute(
            new CancelQuestionRewardIntentCommand(event.postId(), event.acceptedCommentId()));
    log.info(
        "QUESTION_REWARD cancel handled: postId={}, acceptedCommentId={}, found={}, changed={}, status={}",
        event.postId(),
        event.acceptedCommentId(),
        result.found(),
        result.changed(),
        result.status());
  }
}
