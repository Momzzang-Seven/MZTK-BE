package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateQuestionRewardExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RecordQuestionRewardIntentCreationFailureUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.RegisterQuestionRewardIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.domain.event.QuestionRewardIntentRequestedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QuestionRewardIntentRequestedEventHandler {

  private static final String EXECUTION_INTENT_CREATE_FAILED = "EXECUTION_INTENT_CREATE_FAILED";

  private final RegisterQuestionRewardIntentUseCase registerQuestionRewardIntentUseCase;
  private final CreateQuestionRewardExecutionIntentUseCase
      createQuestionRewardExecutionIntentUseCase;
  private final RecordQuestionRewardIntentCreationFailureUseCase
      recordQuestionRewardIntentCreationFailureUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(QuestionRewardIntentRequestedEvent event) {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(
            event.postId(),
            event.acceptedCommentId(),
            event.fromUserId(),
            event.toUserId(),
            event.amountWei());
    try {
      registerQuestionRewardIntentUseCase.execute(command);
    } catch (Exception e) {
      log.error(
          "QUESTION_REWARD legacy intent registration failed after commit: postId={}, acceptedCommentId={}, fromUserId={}, toUserId={}",
          event.postId(),
          event.acceptedCommentId(),
          event.fromUserId(),
          event.toUserId(),
          e);
      return;
    }

    try {
      TransferExecutionIntentResult executionIntent =
          createQuestionRewardExecutionIntentUseCase.execute(command);

      log.info(
          "QUESTION_REWARD intent registered from event: postId={}, acceptedCommentId={}, fromUserId={}, toUserId={}, executionIntentId={}, existing={}",
          event.postId(),
          event.acceptedCommentId(),
          event.fromUserId(),
          event.toUserId(),
          executionIntent.executionIntentId(),
          executionIntent.existing());
    } catch (Exception e) {
      recordCreationFailure(event.postId(), e);
      log.error(
          "QUESTION_REWARD execution intent creation failed after commit: postId={}, acceptedCommentId={}, fromUserId={}, toUserId={}",
          event.postId(),
          event.acceptedCommentId(),
          event.fromUserId(),
          event.toUserId(),
          e);
    }
  }

  private void recordCreationFailure(Long postId, Exception e) {
    try {
      recordQuestionRewardIntentCreationFailureUseCase.execute(
          postId, resolveErrorCode(e), resolveErrorReason(e));
    } catch (Exception persistError) {
      log.error(
          "failed to persist QUESTION_REWARD execution intent creation failure: postId={}",
          postId,
          persistError);
    }
  }

  private String resolveErrorCode(Exception e) {
    if (e instanceof BusinessException businessException) {
      return businessException.getCode();
    }
    return EXECUTION_INTENT_CREATE_FAILED;
  }

  private String resolveErrorReason(Exception e) {
    if (e.getMessage() == null || e.getMessage().isBlank()) {
      return e.getClass().getSimpleName();
    }
    return e.getMessage();
  }
}
