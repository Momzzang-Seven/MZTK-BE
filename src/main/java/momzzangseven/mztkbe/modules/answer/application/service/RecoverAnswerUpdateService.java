package momzzangseven.mztkbe.modules.answer.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException;
import momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.RecoverAnswerUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerUpdateStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class RecoverAnswerUpdateService implements RecoverAnswerUpdateUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final LoadPostPort loadPostPort;
  private final CountAnswersPort countAnswersPort;
  private final AnswerUpdateStatePort answerUpdateStatePort;
  private final AnswerLifecycleExecutionPort answerLifecycleExecutionPort;
  private TransactionOperations transactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  @Override
  public AnswerMutationResult recoverAnswerUpdate(RecoverAnswerUpdateCommand command) {
    command.validate();
    UpdateRecoveryPreparation preparation =
        runInTransaction(() -> prepareLocalUpdateRecovery(command));
    var web3 =
        answerLifecycleExecutionPort
            .prepareAnswerUpdate(
                command.postId(),
                command.answerId(),
                command.requesterId(),
                preparation.questionWriterId(),
                preparation.questionContent(),
                preparation.reward(),
                preparation.updateState().pendingContent(),
                preparation.activeAnswerCount(),
                preparation.updateState().updateVersion(),
                preparation.updateState().updateToken())
            .orElse(null);
    if (web3 != null) {
      int bound =
          runInTransaction(
              () ->
                  answerUpdateStatePort.bindRecoveryIntentIfCurrent(
                      preparation.updateState().id(), web3.executionIntent().id()));
      if (bound == 0) {
        boolean alreadyBoundToSameIntent =
            answerUpdateStatePort
                .loadIntentBoundState(
                    command.answerId(),
                    preparation.updateState().updateVersion(),
                    preparation.updateState().updateToken(),
                    web3.executionIntent().id())
                .isPresent();
        if (!alreadyBoundToSameIntent) {
          answerLifecycleExecutionPort.cancelSignableIntent(
              web3.executionIntent().id(), "answer update recovery intent bind failed");
          throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
        }
      }
    }
    return new AnswerMutationResult(
        command.postId(),
        command.answerId(),
        preparation.publicationStatus(),
        web3 == null ? null : AnswerUpdateStatus.INTENT_BOUND,
        web3 == null ? null : preparation.updateState().updateVersion(),
        web3);
  }

  private UpdateRecoveryPreparation prepareLocalUpdateRecovery(RecoverAnswerUpdateCommand command) {
    Answer answer =
        loadAnswerPort
            .loadAnswerForUpdate(command.answerId())
            .orElseThrow(AnswerNotFoundException::new);
    if (!answer.getPostId().equals(command.postId())) {
      throw new AnswerPostMismatchException();
    }
    answer.validateOwnership(command.requesterId());
    if (!answer.isPubliclyVisible()) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
    }
    AnswerUpdateStatePort.AnswerUpdateState updateState =
        answerUpdateStatePort
            .loadLatestRecoverable(command.answerId())
            .orElseThrow(
                () ->
                    new AnswerPublicationStateException(
                        ErrorCode.ANSWER_UPDATE_RECOVERY_UNAVAILABLE));
    LoadPostPort.PostContext post =
        loadPostPort.loadPost(command.postId()).orElseThrow(AnswerPostNotFoundException::new);
    int activeAnswerCount =
        Math.toIntExact(countAnswersPort.countOnchainBlockingAnswers(command.postId()));
    return new UpdateRecoveryPreparation(
        post.writerId(),
        post.content(),
        post.reward(),
        activeAnswerCount,
        answer.getPublicationStatus(),
        updateState);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private record UpdateRecoveryPreparation(
      Long questionWriterId,
      String questionContent,
      Long reward,
      int activeAnswerCount,
      momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus publicationStatus,
      AnswerUpdateStatePort.AnswerUpdateState updateState) {}
}
