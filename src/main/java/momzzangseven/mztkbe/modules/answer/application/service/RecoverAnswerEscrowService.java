package momzzangseven.mztkbe.modules.answer.application.service;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;
import momzzangseven.mztkbe.global.error.answer.AnswerPublicationStateException;
import momzzangseven.mztkbe.global.error.answer.AnswerUnsupportedPostTypeException;
import momzzangseven.mztkbe.global.error.answer.CannotAnswerSolvedPostException;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerMutationResult;
import momzzangseven.mztkbe.modules.answer.application.dto.RecoverAnswerEscrowCommand;
import momzzangseven.mztkbe.modules.answer.application.port.in.RecoverAnswerEscrowUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadPostPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerLifecycleAction;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/** Recreates an answer-submit intent for a local answer that is still missing projection. */
@Service
@RequiredArgsConstructor
public class RecoverAnswerEscrowService implements RecoverAnswerEscrowUseCase {

  private final LoadAnswerPort loadAnswerPort;
  private final LoadPostPort loadPostPort;
  private final SaveAnswerPort saveAnswerPort;
  private final CountAnswersPort countAnswersPort;
  private final AnswerLifecycleExecutionPort answerLifecycleExecutionPort;
  private TransactionOperations transactionOperations;

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  @Override
  public AnswerMutationResult recoverAnswerCreate(RecoverAnswerEscrowCommand command) {
    command.validate();

    RecoveryPreparation preparation = runInTransaction(() -> prepareLocalRecovery(command));
    var web3 =
        answerLifecycleExecutionPort
            .recoverAnswerCreate(
                command.postId(),
                command.answerId(),
                command.requesterId(),
                preparation.questionWriterId(),
                preparation.questionContent(),
                preparation.reward(),
                preparation.answerContent(),
                preparation.activeAnswerCount())
            .orElse(null);
    if (preparation.managedCreate() && web3 != null) {
      int bound =
          runInTransaction(
              () -> {
                Answer answer =
                    loadAnswerPort
                        .loadAnswerForUpdate(command.answerId())
                        .orElseThrow(
                            momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException::new);
                if (answer.getPublicationStatus() != AnswerPublicationStatus.FAILED) {
                  return 0;
                }
                String preparationToken = UUID.randomUUID().toString();
                saveAnswerPort.saveAnswer(
                    answer.reserveCreate(preparationToken, LocalDateTime.now().plusMinutes(15)));
                return saveAnswerPort.bindCreateIntentIfCurrent(
                    command.answerId(), preparationToken, web3.executionIntent().id());
              });
      if (bound == 0) {
        answerLifecycleExecutionPort.cancelSignableIntent(
            web3.executionIntent().id(), "answer create recovery intent bind failed");
        throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_STATE_CONFLICT);
      }
    }
    return new AnswerMutationResult(
        command.postId(), command.answerId(), preparation.publicationStatus(), web3);
  }

  private RecoveryPreparation prepareLocalRecovery(RecoverAnswerEscrowCommand command) {
    Answer answer =
        loadAnswerPort
            .loadAnswerForUpdate(command.answerId())
            .orElseThrow(momzzangseven.mztkbe.global.error.answer.AnswerNotFoundException::new);
    if (!answer.getPostId().equals(command.postId())) {
      throw new momzzangseven.mztkbe.global.error.answer.AnswerPostMismatchException();
    }
    answer.validateOwnership(command.requesterId());
    boolean managedCreate =
        answerLifecycleExecutionPort.managesAnswerLifecycle(AnswerLifecycleAction.CREATE);
    if (managedCreate && answer.getPublicationStatus() == AnswerPublicationStatus.PENDING) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_PUBLICATION_PENDING);
    }
    if (managedCreate && answer.getPublicationStatus() != AnswerPublicationStatus.FAILED) {
      throw new AnswerPublicationStateException(ErrorCode.ANSWER_CREATE_RECOVERY_UNAVAILABLE);
    }
    LoadPostPort.PostContext post =
        loadPostPort
            .loadPost(command.postId())
            .orElseThrow(momzzangseven.mztkbe.global.error.answer.AnswerPostNotFoundException::new);
    validateAnswerablePost(post);
    validatePostWritable(post);
    answerLifecycleExecutionPort.precheckAnswerCreate(post.postId(), post.content());
    int activeAnswerCount =
        Math.toIntExact(countAnswersPort.countOnchainBlockingAnswers(command.postId()) + 1);
    return new RecoveryPreparation(
        post.writerId(),
        post.content(),
        post.reward(),
        answer.getContent(),
        activeAnswerCount,
        managedCreate,
        answer.getPublicationStatus());
  }

  private void validateAnswerablePost(LoadPostPort.PostContext post) {
    if (!post.questionPost()) {
      throw new AnswerUnsupportedPostTypeException();
    }
    if (post.answerLocked()) {
      throw new CannotAnswerSolvedPostException();
    }
  }

  private void validatePostWritable(LoadPostPort.PostContext post) {
    if (!post.writable()) {
      throw new AnswerInvalidInputException(
          "Post is not in a state that allows answer interactions.");
    }
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private record RecoveryPreparation(
      Long questionWriterId,
      String questionContent,
      Long reward,
      String answerContent,
      int activeAnswerCount,
      boolean managedCreate,
      AnswerPublicationStatus publicationStatus) {}
}
