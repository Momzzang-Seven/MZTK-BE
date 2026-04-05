package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QuestionRewardExecutionActionHandler implements ExecutionActionHandlerPort {

  private final ObjectMapper objectMapper;
  private final Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  private final QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return actionType == ExecutionActionType.QNA_ANSWER_ACCEPT;
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    QuestionRewardExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    String transferData =
        eip7702TransactionCodecPort.encodeTransferData(payload.toAddress(), payload.amountWei());
    return new ExecutionActionPlan(
        payload.amountWei(),
        Web3ReferenceType.USER_TO_USER,
        List.of(
            new ExecutionDraftCall(payload.tokenContractAddress(), BigInteger.ZERO, transferData)));
  }

  @Override
  public void beforeExecute(ExecutionIntent intent, ExecutionActionPlan actionPlan) {
    QuestionRewardExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    questionRewardIntentPersistencePort
        .findByPostId(payload.postId())
        .ifPresentOrElse(
            legacyIntent ->
                log.debug(
                    "legacy QuestionRewardIntent found for postId={}, status={}",
                    payload.postId(),
                    legacyIntent.getStatus()),
            () ->
                log.warn(
                    "legacy QuestionRewardIntent not found for postId={}, "
                        + "ExecutionIntent {} is authoritative",
                    payload.postId(),
                    intent.getPublicId()));
  }

  @Override
  public void afterTransactionSubmitted(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, Web3TxStatus txStatus) {
    QuestionRewardExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    if (txStatus != Web3TxStatus.SIGNED && txStatus != Web3TxStatus.PENDING) {
      return;
    }
    questionRewardIntentPersistencePort.updateStatusIfCurrentIn(
        payload.postId(),
        QuestionRewardIntentStatus.SUBMITTED,
        EnumSet.of(QuestionRewardIntentStatus.PREPARE_REQUIRED));
  }

  private QuestionRewardExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, QuestionRewardExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize question reward execution payload", e);
    }
  }
}
