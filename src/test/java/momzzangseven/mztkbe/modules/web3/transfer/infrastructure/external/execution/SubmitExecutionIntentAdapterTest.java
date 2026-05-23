package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transfer.TransferExecutionActionHandlerAdapter;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionPayload;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionActionType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmitExecutionIntentAdapter")
class SubmitExecutionIntentAdapterTest {

  private static final LocalDateTime EXPIRES_AT = LocalDateTime.parse("2026-04-07T12:00:00");
  private static final String TOKEN = "0x" + "1".repeat(40);
  private static final String AUTHORITY = "0x" + "2".repeat(40);
  private static final String RECEIVER = "0x" + "3".repeat(40);
  private static final String DELEGATE = "0x" + "4".repeat(40);
  private static final String TRANSFER_DATA = "0xa9059cbb" + "0".repeat(128);

  @Mock private CreateExecutionIntentUseCase createExecutionIntentUseCase;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("TRANSFER_SEND draft calls and runtime actionPlan calls are identical")
  void submitTransferDraft_matchesTransferActionPlanCalls() throws Exception {
    SubmitExecutionIntentAdapter submitAdapter =
        new SubmitExecutionIntentAdapter(createExecutionIntentUseCase);
    TransferExecutionActionHandlerAdapter actionHandler =
        new TransferExecutionActionHandlerAdapter(objectMapper);
    ArgumentCaptor<CreateExecutionIntentCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateExecutionIntentCommand.class);
    when(createExecutionIntentUseCase.execute(any()))
        .thenReturn(
            result(ExecutionResourceType.TRANSFER, ExecutionResourceStatus.PENDING_EXECUTION));

    submitAdapter.submit(transferDraft());

    org.mockito.Mockito.verify(createExecutionIntentUseCase).execute(commandCaptor.capture());
    ExecutionDraft createdDraft = commandCaptor.getValue().draft();
    ExecutionIntent runtimeIntent = intentFromDraft(createdDraft);
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(runtimeIntent);

    assertThat(actionPlan.calls()).containsExactlyElementsOf(createdDraft.calls());
  }

  private TransferExecutionDraft transferDraft() throws JsonProcessingException {
    TransferExecutionPayload payload =
        new TransferExecutionPayload(
            "request-1",
            7L,
            8L,
            AUTHORITY,
            RECEIVER,
            TOKEN,
            BigInteger.valueOf(123),
            TRANSFER_DATA);
    return new TransferExecutionDraft(
        TransferExecutionResourceType.TRANSFER,
        "web3:TRANSFER_SEND:7:request-1",
        TransferExecutionResourceStatus.PENDING_EXECUTION,
        TransferExecutionActionType.TRANSFER_SEND,
        7L,
        8L,
        "root-transfer-request-1",
        "0x" + "a".repeat(64),
        objectMapper.writeValueAsString(payload),
        List.of(new TransferExecutionDraftCall(TOKEN, BigInteger.ZERO, TRANSFER_DATA)),
        true,
        AUTHORITY,
        12L,
        DELEGATE,
        "0x" + "b".repeat(64),
        null,
        null,
        EXPIRES_AT);
  }

  private CreateExecutionIntentResult result(
      ExecutionResourceType resourceType, ExecutionResourceStatus resourceStatus) {
    return new CreateExecutionIntentResult(
        resourceType,
        "resource-1",
        resourceStatus,
        "intent-1",
        ExecutionIntentStatus.SIGNED,
        EXPIRES_AT,
        1L,
        ExecutionMode.EIP7702,
        2,
        null,
        false);
  }

  private ExecutionIntent intentFromDraft(ExecutionDraft draft) {
    return ExecutionIntent.create(
        "intent-1",
        draft.rootIdempotencyKey(),
        1,
        ExecutionResourceType.valueOf(draft.resourceType().name()),
        draft.resourceId(),
        ExecutionActionType.valueOf(draft.actionType().name()),
        draft.requesterUserId(),
        draft.counterpartyUserId(),
        ExecutionMode.EIP7702,
        draft.payloadHash(),
        draft.payloadSnapshotJson(),
        draft.authorityAddress(),
        draft.authorityNonce(),
        draft.delegateTarget(),
        draft.expiresAt(),
        draft.authorizationPayloadHash(),
        "0x" + "c".repeat(64),
        null,
        null,
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 7),
        EXPIRES_AT.minusMinutes(5));
  }
}
