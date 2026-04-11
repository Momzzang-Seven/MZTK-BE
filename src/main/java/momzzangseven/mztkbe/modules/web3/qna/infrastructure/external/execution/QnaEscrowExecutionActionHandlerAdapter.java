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
}
