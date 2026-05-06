package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.math.BigInteger;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional
public class AnswerEscrowExecutionService implements AnswerEscrowExecutionUseCase {

  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  private final SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @Override
  public boolean hasActiveAnswerIntent(Long answerId) {
    if (answerId == null) {
      return false;
    }
    return loadQnaExecutionIntentStatePort
        .loadLatestActiveByResource(QnaExecutionResourceType.ANSWER, String.valueOf(answerId))
        .isPresent();
  }

  @Override
  public void precheckAnswerCreate(PrecheckAnswerCreateCommand command) {
    command.validate();
    if (loadQnaExecutionIntentStatePort
        .loadLatestActiveByResource(
            QnaExecutionResourceType.QUESTION, String.valueOf(command.postId()))
        .isPresent()) {
      throw new Web3InvalidInputException(
          "question has active onchain mutation in progress: postId=" + command.postId());
    }
    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    String localQuestionHash = QnaContentHashFactory.hash(command.questionContent());
    if (!localQuestionHash.equals(question.getQuestionHash())) {
      throw new Web3InvalidInputException(
          "question content differs from latest onchain projection; recover or wait for sync");
    }
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerCreate(PrepareAnswerCreateCommand command) {
    command.validate();

    ensureAnswerMutationConflictFree(command.answerId(), QnaExecutionActionType.QNA_ANSWER_SUBMIT);
    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    RewardContext rewardContext = rewardContext(question);
    String answerHash = QnaContentHashFactory.hash(command.answerContent());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            String.valueOf(command.answerId()),
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            command.requesterUserId(),
            command.questionWriterUserId(),
            command.postId(),
            command.answerId(),
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            answerHash));
  }

  @Override
  public QnaExecutionIntentResult recoverAnswerCreate(PrepareAnswerCreateCommand command) {
    command.validate();
    ensureAnswerCreateRecoverable(command.postId(), command.answerId(), command.requesterUserId());
    return prepareAnswerCreate(command);
  }

  @Override
  public Optional<QnaExecutionIntentResult> recoverAnswerUpdate(
      PrepareAnswerUpdateCommand command) {
    command.validate();

    QnaAnswerProjection answer = requireAnswerProjection(command.answerId());
    String localAnswerHash = QnaContentHashFactory.hash(command.answerContent());
    if (localAnswerHash.equals(answer.getContentHash())) {
      return Optional.empty();
    }
    if (!isAnswerUpdateRecoverable(
        command.postId(), command.answerId(), command.requesterUserId())) {
      return Optional.empty();
    }
    return Optional.of(prepareAnswerUpdate(command));
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerUpdate(PrepareAnswerUpdateCommand command) {
    command.validate();

    ensureAnswerMutationConflictFree(command.answerId(), QnaExecutionActionType.QNA_ANSWER_UPDATE);
    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    RewardContext rewardContext = rewardContext(question);
    String answerHash = QnaContentHashFactory.hash(command.answerContent());
    requireAnswerProjection(command.answerId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            String.valueOf(command.answerId()),
            QnaExecutionActionType.QNA_ANSWER_UPDATE,
            command.requesterUserId(),
            command.questionWriterUserId(),
            command.postId(),
            command.answerId(),
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            answerHash));
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerDelete(PrepareAnswerDeleteCommand command) {
    command.validate();

    ensureAnswerMutationConflictFree(command.answerId(), QnaExecutionActionType.QNA_ANSWER_DELETE);
    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    RewardContext rewardContext = rewardContext(question);
    requireAnswerProjection(command.answerId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            String.valueOf(command.answerId()),
            QnaExecutionActionType.QNA_ANSWER_DELETE,
            command.requesterUserId(),
            command.questionWriterUserId(),
            command.postId(),
            command.answerId(),
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            null));
  }

  private QnaQuestionProjection requireQuestionProjection(Long postId) {
    return qnaProjectionPersistencePort
        .findQuestionByPostIdForUpdate(postId)
        .orElseThrow(
            () ->
                new Web3InvalidInputException(
                    "question is not registered onchain yet: postId=" + postId));
  }

  private QnaAnswerProjection requireAnswerProjection(Long answerId) {
    return qnaProjectionPersistencePort
        .findAnswerByAnswerIdForUpdate(answerId)
        .orElseThrow(
            () ->
                new Web3InvalidInputException(
                    "answer is not registered onchain yet: answerId=" + answerId));
  }

  private RewardContext rewardContext(QnaQuestionProjection question) {
    return new RewardContext(question.getTokenAddress(), question.getRewardAmountWei());
  }

  private void ensureAnswerCreateRecoverable(Long postId, Long answerId, Long requesterUserId) {
    if (qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(answerId).isPresent()) {
      throw new Web3InvalidInputException(
          "answer is already registered onchain: answerId=" + answerId);
    }
    String rootIdempotencyKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_ANSWER_SUBMIT, requesterUserId, postId, answerId);
    loadQnaExecutionIntentStatePort
        .loadLatestByRootIdempotencyKey(rootIdempotencyKey)
        .filter(it -> it.matchesAction(QnaExecutionActionType.QNA_ANSWER_SUBMIT) && it.isTerminal())
        .orElseThrow(
            () ->
                new Web3InvalidInputException(
                    "answer create recovery is not available: answerId=" + answerId));
  }

  private void ensureAnswerMutationConflictFree(
      Long answerId, QnaExecutionActionType requestedActionType) {
    if (loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(
        QnaExecutionResourceType.ANSWER, String.valueOf(answerId), requestedActionType)) {
      throw new Web3InvalidInputException(
          "conflicting active answer execution intent exists: answerId=" + answerId);
    }
  }

  private boolean isAnswerUpdateRecoverable(Long postId, Long answerId, Long requesterUserId) {
    String rootIdempotencyKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_ANSWER_UPDATE, requesterUserId, postId, answerId);
    return loadQnaExecutionIntentStatePort
        .loadLatestByRootIdempotencyKey(rootIdempotencyKey)
        .filter(it -> it.matchesAction(QnaExecutionActionType.QNA_ANSWER_UPDATE) && it.isTerminal())
        .isPresent();
  }

  private QnaExecutionIntentResult submit(QnaEscrowExecutionRequest request) {
    return submitQnaExecutionDraftPort.submit(buildQnaExecutionDraftPort.build(request));
  }

  private record RewardContext(String tokenAddress, BigInteger amountWei) {}
}
