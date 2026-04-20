package momzzangseven.mztkbe.modules.web3.qna.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAdminSettleCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

@RequiredArgsConstructor
public class QuestionEscrowAdminExecutionService {

  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final LoadQnaAnswerIdsPort loadQnaAnswerIdsPort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final BuildQnaAdminExecutionDraftPort buildQnaAdminExecutionDraftPort;
  private final SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  public QnaExecutionIntentResult prepareAdminSettle(PrepareAdminSettleCommand command) {
    command.validate();

    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    QnaAnswerProjection answer = requireAnswerProjection(command.answerId());
    ensureQuestionMutationConflictFree(command.postId(), QnaExecutionActionType.QNA_ADMIN_SETTLE);
    ensureAnswerMutationConflictFree(command.answerId(), QnaExecutionActionType.QNA_ADMIN_SETTLE);
    ensureHashesMatch(
        command.questionContent(),
        question.getQuestionHash(),
        command.answerContent(),
        answer.getContentHash());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_ADMIN_SETTLE,
            command.requesterUserId(),
            command.answerWriterUserId(),
            command.postId(),
            command.answerId(),
            question.getTokenAddress(),
            question.getRewardAmountWei(),
            question.getQuestionHash(),
            answer.getContentHash()));
  }

  public QnaExecutionIntentResult prepareAdminRefund(PrepareAdminRefundCommand command) {
    command.validate();

    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    ensureQuestionMutationConflictFree(command.postId(), QnaExecutionActionType.QNA_ADMIN_REFUND);
    ensureQuestionAnswerMutationsConflictFree(command.postId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_ADMIN_REFUND,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            question.getTokenAddress(),
            question.getRewardAmountWei(),
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

  private void ensureQuestionAnswerMutationsConflictFree(Long postId) {
    for (Long answerId : loadAnswerIdsForRefund(postId)) {
      if (loadQnaExecutionIntentStatePort.hasActiveIntentForUpdate(
          QnaExecutionResourceType.ANSWER, String.valueOf(answerId))) {
        throw new Web3InvalidInputException(
            "conflicting active answer execution intent exists for refund: postId=" + postId);
      }
    }
  }

  private java.util.Set<Long> loadAnswerIdsForRefund(Long postId) {
    java.util.Set<Long> answerIds =
        new java.util.LinkedHashSet<>(loadQnaAnswerIdsPort.loadAnswerIdsByPostId(postId));
    qnaProjectionPersistencePort.findAnswersByPostIdForUpdate(postId).stream()
        .map(QnaAnswerProjection::getAnswerId)
        .forEach(answerIds::add);
    return answerIds;
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
    return submitQnaExecutionDraftPort.submit(buildQnaAdminExecutionDraftPort.build(request));
  }
}
