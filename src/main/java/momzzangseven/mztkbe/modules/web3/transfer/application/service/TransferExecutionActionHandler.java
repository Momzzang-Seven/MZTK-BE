package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class TransferExecutionActionHandler implements ExecutionActionHandlerPort {

  private final ObjectMapper objectMapper;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return actionType == ExecutionActionType.TRANSFER_SEND;
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    TransferExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    String transferData =
        eip7702TransactionCodecPort.encodeTransferData(payload.toAddress(), payload.amountWei());
    return new ExecutionActionPlan(
        payload.amountWei(),
        Web3ReferenceType.USER_TO_USER,
        List.of(
            new ExecutionDraftCall(payload.tokenContractAddress(), BigInteger.ZERO, transferData)));
  }

  private TransferExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, TransferExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize transfer payload", e);
    }
  }
}
