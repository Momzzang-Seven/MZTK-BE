package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.math.BigInteger;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

@RequiredArgsConstructor
public class QuestionEscrowExecutionService implements QuestionEscrowExecutionUseCase {

  private final PrecheckQuestionFundingPort precheckQuestionFundingPort;
  private final LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort;
  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  private final SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @Override
  public boolean hasActiveQuestionIntent(Long postId) {
    if (postId == null) {
      return false;
    }
    return loadQnaExecutionIntentStatePort
        .loadLatestActiveByResource(QnaExecutionResourceType.QUESTION, String.valueOf(postId))
        .isPresent();
  }

  @Override
  public void precheckQuestionCreate(PrecheckQuestionCreateCommand command) {
    command.validate();
    precheckQuestionFundingPort.precheck(command);
  }

  @Override
  public QnaExecutionIntentResult prepareQuestionCreate(PrepareQuestionCreateCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    String questionHash = QnaContentHashFactory.hash(command.questionContent());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            questionHash,
            null));
  }

  @Override
  public QnaExecutionIntentResult recoverQuestionCreate(PrepareQuestionCreateCommand command) {
    command.validate();
    ensureQuestionCreateRecoverable(command.postId(), command.requesterUserId());
    return prepareQuestionCreate(command);
  }

  @Override
  public Optional<QnaExecutionIntentResult> recoverQuestionUpdate(
      PrepareQuestionUpdateCommand command) {
    command.validate();

    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    String localQuestionHash = QnaContentHashFactory.hash(command.questionContent());
    if (localQuestionHash.equals(question.getQuestionHash())) {
      return Optional.empty();
    }
    if (!isQuestionUpdateRecoverable(command.postId(), command.requesterUserId())) {
      return Optional.empty();
    }
    return Optional.of(prepareQuestionUpdate(command));
  }

  @Override
  public QnaExecutionIntentResult prepareQuestionUpdate(PrepareQuestionUpdateCommand command) {
    command.validate();

    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    ensureQuestionMutationAllowed(question, QnaExecutionActionType.QNA_QUESTION_UPDATE);
    RewardContext rewardContext = rewardContext(question);
    String questionHash = QnaContentHashFactory.hash(command.questionContent());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_QUESTION_UPDATE,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            questionHash,
            null));
  }

  @Override
  public QnaExecutionIntentResult prepareQuestionDelete(PrepareQuestionDeleteCommand command) {
    command.validate();

    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    ensureQuestionMutationAllowed(question, QnaExecutionActionType.QNA_QUESTION_DELETE);
    RewardContext rewardContext = rewardContext(question);

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_QUESTION_DELETE,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            null));
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerAccept(PrepareAnswerAcceptCommand command) {
    command.validate();

    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    QnaAnswerProjection answer = requireAnswerProjection(command.answerId());
    ensureQuestionMutationConflictFree(command.postId(), QnaExecutionActionType.QNA_ANSWER_ACCEPT);
    ensureAnswerMutationConflictFree(command.answerId(), QnaExecutionActionType.QNA_ANSWER_ACCEPT);
    ensureHashesMatch(
        command.questionContent(),
        question.getQuestionHash(),
        command.answerContent(),
        answer.getContentHash());
    RewardContext rewardContext = rewardContext(question);

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            command.requesterUserId(),
            command.answerWriterUserId(),
            command.postId(),
            command.answerId(),
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            answer.getContentHash()));
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

  private RewardContext loadRewardContext(Long rewardMztk) {
    LoadQnaRewardTokenConfigPort.RewardTokenConfig config =
        loadQnaRewardTokenConfigPort.loadRewardTokenConfig();
    BigInteger amountWei = QnaEscrowIdCodec.toAmountWei(rewardMztk, config.decimals());
    return new RewardContext(config.tokenContractAddress(), amountWei);
  }

  private RewardContext rewardContext(QnaQuestionProjection question) {
    return new RewardContext(question.getTokenAddress(), question.getRewardAmountWei());
  }

  private void ensureQuestionCreateRecoverable(Long postId, Long requesterUserId) {
    if (qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(postId).isPresent()) {
      throw new Web3InvalidInputException(
          "question is already registered onchain: postId=" + postId);
    }
    String rootIdempotencyKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_QUESTION_CREATE, requesterUserId, postId, null);
    loadQnaExecutionIntentStatePort
        .loadLatestByRootIdempotencyKey(rootIdempotencyKey)
        .filter(
            it -> it.matchesAction(QnaExecutionActionType.QNA_QUESTION_CREATE) && it.isTerminal())
        .orElseThrow(
            () ->
                new Web3InvalidInputException(
                    "question create recovery is not available: postId=" + postId));
  }

  private void ensureQuestionMutationAllowed(
      QnaQuestionProjection question, QnaExecutionActionType requestedActionType) {
    if (question.getAnswerCount() > 0) {
      throw new Web3InvalidInputException(
          "question has unresolved onchain answers: postId=" + question.getPostId());
    }
    ensureQuestionMutationConflictFree(question.getPostId(), requestedActionType);
  }

  private boolean isQuestionUpdateRecoverable(Long postId, Long requesterUserId) {
    String rootIdempotencyKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_QUESTION_UPDATE, requesterUserId, postId, null);
    return loadQnaExecutionIntentStatePort
        .loadLatestByRootIdempotencyKey(rootIdempotencyKey)
        .filter(
            it -> it.matchesAction(QnaExecutionActionType.QNA_QUESTION_UPDATE) && it.isTerminal())
        .isPresent();
  }

  private void ensureQuestionMutationConflictFree(
      Long postId, QnaExecutionActionType requestedActionType) {
    if (loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(
        QnaExecutionResourceType.QUESTION, String.valueOf(postId), requestedActionType)) {
      throw new Web3InvalidInputException(
          "conflicting active question execution intent exists: postId=" + postId);
    }
  }

  private void ensureAnswerMutationConflictFree(
      Long answerId, QnaExecutionActionType requestedActionType) {
    if (loadQnaExecutionIntentStatePort.hasConflictingActiveIntent(
        QnaExecutionResourceType.ANSWER, String.valueOf(answerId), requestedActionType)) {
      throw new Web3InvalidInputException(
          "conflicting active answer execution intent exists: answerId=" + answerId);
    }
  }

  private void ensureHashesMatch(
      String localQuestionContent,
      String projectedQuestionHash,
      String localAnswerContent,
      String projectedAnswerHash) {
    String localQuestionHash = QnaContentHashFactory.hash(localQuestionContent);
    if (!localQuestionHash.equals(projectedQuestionHash)) {
      throw new Web3InvalidInputException(
          "question content differs from latest onchain projection; recover or wait for sync");
    }
    String localAnswerHash = QnaContentHashFactory.hash(localAnswerContent);
    if (!localAnswerHash.equals(projectedAnswerHash)) {
      throw new Web3InvalidInputException(
          "answer content differs from latest onchain projection; recover or wait for sync");
    }
  }

  private QnaExecutionIntentResult submit(QnaEscrowExecutionRequest request) {
    return submitQnaExecutionDraftPort.submit(buildQnaExecutionDraftPort.build(request));
  }

  private record RewardContext(String tokenAddress, BigInteger amountWei) {}
}
