package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transfer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetQuestionRewardIntentSnapshotQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.MarkQuestionRewardIntentSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.QuestionRewardExecutionPayload;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetQuestionRewardIntentSnapshotUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.MarkQuestionRewardIntentSubmittedUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3.transfer",
    name = "legacy-question-reward-handler-enabled",
    havingValue = "true")
public class QuestionRewardExecutionActionHandlerAdapter implements ExecutionActionHandlerPort {

  private final ObjectMapper objectMapper;
  private final GetQuestionRewardIntentSnapshotUseCase getQuestionRewardIntentSnapshotUseCase;
  private final MarkQuestionRewardIntentSubmittedUseCase markQuestionRewardIntentSubmittedUseCase;

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return actionType == ExecutionActionType.QNA_ANSWER_ACCEPT;
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    QuestionRewardExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    return new ExecutionActionPlan(
        payload.amountWei(),
        ExecutionReferenceType.USER_TO_USER,
        List.of(
            new ExecutionDraftCall(
                payload.tokenContractAddress(), BigInteger.ZERO, payload.transferData())));
  }

  @Override
  public void beforeExecute(ExecutionIntent intent, ExecutionActionPlan actionPlan) {
    QuestionRewardExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    var snapshot =
        getQuestionRewardIntentSnapshotUseCase.execute(
            new GetQuestionRewardIntentSnapshotQuery(payload.postId()));
    if (snapshot.found()) {
      log.debug(
          "legacy QuestionRewardIntent found for postId={}, status={}",
          payload.postId(),
          snapshot.status());
      return;
    }
    log.warn(
        "legacy QuestionRewardIntent not found for postId={}, ExecutionIntent {} is authoritative",
        payload.postId(),
        intent.getPublicId());
  }

  @Override
  public void afterTransactionSubmitted(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, ExecutionTransactionStatus txStatus) {
    if (txStatus != ExecutionTransactionStatus.SIGNED
        && txStatus != ExecutionTransactionStatus.PENDING) {
      return;
    }
    QuestionRewardExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    markQuestionRewardIntentSubmittedUseCase.execute(
        new MarkQuestionRewardIntentSubmittedCommand(payload.postId()));
  }

  private QuestionRewardExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, QuestionRewardExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize question reward execution payload", e);
    }
  }
}
