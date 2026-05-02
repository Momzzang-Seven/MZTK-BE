package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAdminRefundStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionPublicationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdempotencyKeyFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnAnyExecutionEnabled
public class QnaEscrowExecutionActionHandlerAdapter implements ExecutionActionHandlerPort {

  private static final EnumSet<ExecutionActionType> SUPPORTED_ACTIONS =
      EnumSet.of(
          ExecutionActionType.QNA_QUESTION_CREATE,
          ExecutionActionType.QNA_QUESTION_UPDATE,
          ExecutionActionType.QNA_QUESTION_DELETE,
          ExecutionActionType.QNA_ANSWER_SUBMIT,
          ExecutionActionType.QNA_ANSWER_UPDATE,
          ExecutionActionType.QNA_ANSWER_DELETE,
          ExecutionActionType.QNA_ANSWER_ACCEPT,
          ExecutionActionType.QNA_ADMIN_SETTLE,
          ExecutionActionType.QNA_ADMIN_REFUND);

  private final ObjectMapper objectMapper;
  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final QnaAcceptStateSyncPort qnaAcceptStateSyncPort;
  private final QnaAdminRefundStateSyncPort qnaAdminRefundStateSyncPort;
  private final QnaQuestionPublicationSyncPort qnaQuestionPublicationSyncPort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaLocalDeleteSyncPort
      qnaLocalDeleteSyncPort;

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return SUPPORTED_ACTIONS.contains(actionType);
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    QnaEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    return new ExecutionActionPlan(
        payload.amountWei(),
        referenceType(payload.actionType()),
        List.of(new ExecutionDraftCall(payload.callTarget(), BigInteger.ZERO, payload.callData())));
  }

  @Override
  public void afterExecutionConfirmed(ExecutionIntent intent, ExecutionActionPlan actionPlan) {
    QnaEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    switch (payload.actionType()) {
      case QNA_QUESTION_CREATE -> applyQuestionCreate(intent, payload);
      case QNA_QUESTION_UPDATE -> applyQuestionUpdate(payload);
      case QNA_QUESTION_DELETE -> applyQuestionDelete(payload);
      case QNA_ANSWER_SUBMIT -> applyAnswerSubmit(intent, payload);
      case QNA_ANSWER_UPDATE -> applyAnswerUpdate(payload);
      case QNA_ANSWER_DELETE -> applyAnswerDelete(payload);
      case QNA_ANSWER_ACCEPT -> applyAnswerAccept(payload);
      case QNA_ADMIN_SETTLE -> applyAdminSettle(payload);
      case QNA_ADMIN_REFUND -> applyAdminRefund(payload);
    }
  }

  @Override
  public void afterExecutionFailedOnchain(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, String failureReason) {
    // Failed-onchain specific handling is intentionally empty.
    // The termination hook runner invokes afterExecutionTerminated immediately after this hook,
    // and rollback belongs to that terminal callback.
  }

  @Override
  public void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    QnaEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    if (payload.actionType() == QnaExecutionActionType.QNA_QUESTION_CREATE) {
      if (shouldFailQuestionCreate(intent, payload)) {
        qnaQuestionPublicationSyncPort.failQuestionCreate(
            payload.postId(), intent.getPublicId(), terminalStatus, failureReason);
      }
      return;
    }
    if (!shouldRollbackPendingState(terminalStatus, failureReason)) {
      return;
    }
    if (payload.actionType() == QnaExecutionActionType.QNA_ANSWER_ACCEPT
        || payload.actionType() == QnaExecutionActionType.QNA_ADMIN_SETTLE) {
      qnaAcceptStateSyncPort.rollbackPendingAccept(payload.postId(), payload.answerId());
      return;
    }
    if (payload.actionType() == QnaExecutionActionType.QNA_ADMIN_REFUND) {
      qnaAdminRefundStateSyncPort.rollbackPendingRefund(payload.postId());
    }
  }

  private boolean shouldRollbackPendingState(
      ExecutionIntentStatus terminalStatus, String failureReason) {
    if (terminalStatus == ExecutionIntentStatus.EXPIRED
        || terminalStatus == ExecutionIntentStatus.CANCELED
        || terminalStatus == ExecutionIntentStatus.NONCE_STALE) {
      return true;
    }
    if (failureReason == null || failureReason.isBlank()) {
      return true;
    }
    Web3TxFailureReason reason = resolveFailureReason(failureReason);
    return reason == null || !reason.isRetryable();
  }

  private Web3TxFailureReason resolveFailureReason(String failureReason) {
    try {
      return Web3TxFailureReason.valueOf(failureReason);
    } catch (IllegalArgumentException ignored) {
      for (Web3TxFailureReason candidate : Web3TxFailureReason.values()) {
        if (failureReason.startsWith(candidate.code() + "_")) {
          return candidate;
        }
      }
      return null;
    }
  }

  private ExecutionReferenceType referenceType(QnaExecutionActionType actionType) {
    return switch (actionType) {
      case QNA_ANSWER_ACCEPT, QNA_ADMIN_SETTLE -> ExecutionReferenceType.USER_TO_USER;
      default -> ExecutionReferenceType.USER_TO_SERVER;
    };
  }

  private QnaEscrowExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, QnaEscrowExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize qna escrow execution payload", e);
    }
  }

  private void applyQuestionCreate(ExecutionIntent intent, QnaEscrowExecutionPayload payload) {
    qnaProjectionPersistencePort.saveQuestion(
        QnaQuestionProjection.create(
            payload.postId(),
            intent.getRequesterUserId(),
            QnaEscrowIdCodec.questionId(payload.postId()),
            payload.tokenAddress(),
            payload.amountWei(),
            payload.questionHash()));
    qnaQuestionPublicationSyncPort.confirmQuestionCreated(payload.postId(), intent.getPublicId());
  }

  private boolean shouldFailQuestionCreate(
      ExecutionIntent intent, QnaEscrowExecutionPayload payload) {
    if (qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(payload.postId()).isPresent()) {
      return false;
    }
    String rootIdempotencyKey =
        QnaEscrowIdempotencyKeyFactory.create(
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            intent.getRequesterUserId(),
            payload.postId(),
            null);
    return loadQnaExecutionIntentStatePort
        .loadLatestByRootIdempotencyKey(rootIdempotencyKey)
        .map(latest -> intent.getPublicId().equals(latest.executionIntentId()))
        .orElse(true);
  }

  private void applyQuestionUpdate(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    qnaProjectionPersistencePort.saveQuestion(question.updateQuestionHash(payload.questionHash()));
  }

  private void applyQuestionDelete(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    qnaProjectionPersistencePort.saveQuestion(question.markDeleted());
    qnaLocalDeleteSyncPort.confirmQuestionDeleted(payload.postId());
  }

  private void applyAnswerSubmit(ExecutionIntent intent, QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    qnaProjectionPersistencePort.saveQuestion(
        question.syncAnswerCount(question.getAnswerCount() + 1));
    qnaProjectionPersistencePort.saveAnswer(
        QnaAnswerProjection.create(
            payload.answerId(),
            payload.postId(),
            QnaEscrowIdCodec.questionId(payload.postId()),
            QnaEscrowIdCodec.answerId(payload.answerId()),
            intent.getRequesterUserId(),
            payload.contentHash()));
  }

  private void applyAnswerUpdate(QnaEscrowExecutionPayload payload) {
    QnaAnswerProjection answer = requireAnswer(payload.answerId());
    qnaProjectionPersistencePort.saveAnswer(answer.updateContentHash(payload.contentHash()));
  }

  private void applyAnswerDelete(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    requireAnswer(payload.answerId());
    qnaProjectionPersistencePort.deleteAnswerByAnswerId(payload.answerId());
    qnaProjectionPersistencePort.saveQuestion(
        question.syncAnswerCount(question.getAnswerCount() - 1));
    qnaLocalDeleteSyncPort.confirmAnswerDeleted(payload.answerId());
  }

  private void applyAnswerAccept(QnaEscrowExecutionPayload payload) {
    qnaAcceptStateSyncPort.confirmAccepted(payload.postId(), payload.answerId());
    QnaQuestionProjection question = requireQuestion(payload.postId());
    QnaAnswerProjection answer = requireAnswer(payload.answerId());
    qnaProjectionPersistencePort.saveAnswer(answer.markAccepted());
    qnaProjectionPersistencePort.saveQuestion(
        question.updateQuestionHash(payload.questionHash()).markAccepted(answer.getAnswerKey()));
  }

  private void applyAdminSettle(QnaEscrowExecutionPayload payload) {
    qnaAcceptStateSyncPort.confirmAccepted(payload.postId(), payload.answerId());
    QnaQuestionProjection question = requireQuestion(payload.postId());
    QnaAnswerProjection answer = requireAnswer(payload.answerId());
    qnaProjectionPersistencePort.saveAnswer(answer.markAccepted());
    qnaProjectionPersistencePort.saveQuestion(
        question
            .updateQuestionHash(payload.questionHash())
            .markAdminSettled(answer.getAnswerKey()));
  }

  private void applyAdminRefund(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    QnaQuestionProjection refunded =
        question.getAnswerCount() > 0 ? question.markDeletedWithAnswers() : question.markDeleted();
    qnaProjectionPersistencePort.saveQuestion(refunded);
    qnaLocalDeleteSyncPort.confirmQuestionDeleted(payload.postId());
  }

  private QnaQuestionProjection requireQuestion(Long postId) {
    return qnaProjectionPersistencePort
        .findQuestionByPostIdForUpdate(postId)
        .orElseThrow(
            () -> new IllegalStateException("missing qna question projection: postId=" + postId));
  }

  private QnaAnswerProjection requireAnswer(Long answerId) {
    return qnaProjectionPersistencePort
        .findAnswerByAnswerIdForUpdate(answerId)
        .orElseThrow(
            () -> new IllegalStateException("missing qna answer projection: answerId=" + answerId));
  }
}
