package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAnswerExecutionIntentRefPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAnswerExecutionIntentRefPersistencePort.QnaAnswerExecutionIntentRef;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
public class SubmitQnaExecutionIntentAdapter implements SubmitQnaExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;
  private final ObjectMapper objectMapper;
  private final QnaAnswerExecutionIntentRefPersistencePort refPersistencePort;

  @Override
  public QnaExecutionIntentResult submit(QnaExecutionDraft draft) {
    CreateExecutionIntentResult result =
        createExecutionIntentUseCase.execute(
            new CreateExecutionIntentCommand(toExecutionDraft(draft)));
    QnaExecutionIntentResult qnaResult =
        QnaExecutionIntentResult.from(draft.actionType().name(), result);
    upsertAnswerExecutionRef(draft, qnaResult);
    return qnaResult;
  }

  private void upsertAnswerExecutionRef(QnaExecutionDraft draft, QnaExecutionIntentResult result) {
    if (draft.actionType() != QnaExecutionActionType.QNA_ANSWER_SUBMIT
        && draft.actionType() != QnaExecutionActionType.QNA_ANSWER_UPDATE
        && draft.actionType() != QnaExecutionActionType.QNA_ANSWER_DELETE) {
      return;
    }
    QnaEscrowExecutionPayload payload = readPayload(draft.payloadSnapshotJson());
    if (payload.postId() == null || payload.answerId() == null) {
      return;
    }
    refPersistencePort.upsert(
        new QnaAnswerExecutionIntentRef(
            result.executionIntent().id(),
            payload.postId(),
            payload.answerId(),
            draft.actionType(),
            result.executionIntent().status()));
  }

  private QnaEscrowExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, QnaEscrowExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize qna escrow execution payload", e);
    }
  }

  private ExecutionDraft toExecutionDraft(QnaExecutionDraft draft) {
    return new ExecutionDraft(
        ExecutionResourceTypeCode.valueOf(draft.resourceType().name()),
        draft.resourceId(),
        ExecutionResourceStatusCode.valueOf(draft.resourceStatus().name()),
        ExecutionActionTypeCode.valueOf(draft.actionType().name()),
        draft.requesterUserId(),
        draft.counterpartyUserId(),
        draft.rootIdempotencyKey(),
        draft.payloadHash(),
        draft.payloadSnapshotJson(),
        toCalls(draft.calls()),
        draft.fallbackAllowed(),
        draft.authorityAddress(),
        draft.authorityNonce(),
        draft.delegateTarget(),
        draft.authorizationPayloadHash(),
        toUnsignedTxSnapshot(draft.unsignedTxSnapshot()),
        draft.unsignedTxFingerprint(),
        draft.expiresAt());
  }

  private List<ExecutionDraftCall> toCalls(List<QnaExecutionDraftCall> calls) {
    return calls.stream()
        .map(call -> new ExecutionDraftCall(call.target(), call.value(), call.data()))
        .toList();
  }

  private UnsignedTxSnapshot toUnsignedTxSnapshot(QnaUnsignedTxSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return new UnsignedTxSnapshot(
        snapshot.chainId(),
        snapshot.fromAddress(),
        snapshot.toAddress(),
        snapshot.value(),
        snapshot.data(),
        snapshot.nonce(),
        snapshot.gasLimit(),
        snapshot.maxPriorityFeePerGas(),
        snapshot.maxFeePerGas());
  }
}
