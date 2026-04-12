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
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaEscrowExecutionActionHandlerAdapter implements ExecutionActionHandlerPort {

  private static final EnumSet<ExecutionActionType> SUPPORTED_ACTIONS =
      EnumSet.of(
          ExecutionActionType.QNA_QUESTION_CREATE,
          ExecutionActionType.QNA_QUESTION_UPDATE,
          ExecutionActionType.QNA_QUESTION_DELETE,
          ExecutionActionType.QNA_ANSWER_SUBMIT,
          ExecutionActionType.QNA_ANSWER_UPDATE,
          ExecutionActionType.QNA_ANSWER_DELETE,
          ExecutionActionType.QNA_ANSWER_ACCEPT);

  private final ObjectMapper objectMapper;
  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;

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
    }
  }

  private ExecutionReferenceType referenceType(QnaExecutionActionType actionType) {
    return switch (actionType) {
      case QNA_ANSWER_ACCEPT -> ExecutionReferenceType.USER_TO_USER;
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
  }

  private void applyQuestionUpdate(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    qnaProjectionPersistencePort.saveQuestion(question.updateQuestionHash(payload.questionHash()));
  }

  private void applyQuestionDelete(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    qnaProjectionPersistencePort.saveQuestion(question.markDeleted());
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
  }

  private void applyAnswerAccept(QnaEscrowExecutionPayload payload) {
    QnaQuestionProjection question = requireQuestion(payload.postId());
    QnaAnswerProjection answer = requireAnswer(payload.answerId());
    qnaProjectionPersistencePort.saveAnswer(answer.updateContentHash(payload.contentHash()));
    qnaProjectionPersistencePort.saveQuestion(
        question.updateQuestionHash(payload.questionHash()).markAccepted(answer.getAnswerKey()));
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
