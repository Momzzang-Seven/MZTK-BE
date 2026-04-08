package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class TransferExecutionActionHandlerAdapter implements ExecutionActionHandlerPort {

  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return actionType == ExecutionActionType.TRANSFER_SEND;
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    TransferExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    return new ExecutionActionPlan(
        payload.amountWei(),
        ExecutionReferenceType.USER_TO_USER,
        List.of(
            new ExecutionDraftCall(
                payload.tokenContractAddress(), BigInteger.ZERO, payload.transferData())));
  }

  private TransferExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, TransferExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize transfer payload", e);
    }
  }
}
