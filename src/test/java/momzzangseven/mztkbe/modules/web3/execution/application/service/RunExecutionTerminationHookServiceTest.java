package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.RunExecutionTerminationHookCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunExecutionTerminationHookServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 15, 10, 0);

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;

  @Test
  void execute_runsTerminationHookWithRebuiltActionPlan() {
    ExecutionIntent intent = existingEip1559Intent();
    ExecutionActionPlan actionPlan = actionPlan();
    RunExecutionTerminationHookService service =
        new RunExecutionTerminationHookService(
            executionIntentPersistencePort, List.of(executionActionHandlerPort));

    when(executionIntentPersistencePort.findByPublicId("intent-1")).thenReturn(Optional.of(intent));
    when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND)).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(intent)).thenReturn(actionPlan);

    service.execute(
        new RunExecutionTerminationHookCommand(
            "intent-1", ExecutionIntentStatus.EXPIRED, "EXECUTION_INTENT_EXPIRED"));

    verify(executionActionHandlerPort)
        .afterExecutionTerminated(
            same(intent),
            same(actionPlan),
            eq(ExecutionIntentStatus.EXPIRED),
            eq("EXECUTION_INTENT_EXPIRED"));
  }

  @Test
  void execute_runsFailedOnchainHookBeforeTerminationHookForFailedOnchainStatus() {
    ExecutionIntent intent = existingEip1559Intent();
    ExecutionActionPlan actionPlan = actionPlan();
    RunExecutionTerminationHookService service =
        new RunExecutionTerminationHookService(
            executionIntentPersistencePort, List.of(executionActionHandlerPort));

    when(executionIntentPersistencePort.findByPublicId("intent-1")).thenReturn(Optional.of(intent));
    when(executionActionHandlerPort.supports(ExecutionActionType.TRANSFER_SEND)).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(intent)).thenReturn(actionPlan);

    service.execute(
        new RunExecutionTerminationHookCommand(
            "intent-1", ExecutionIntentStatus.FAILED_ONCHAIN, "RECEIPT_STATUS_0"));

    InOrder inOrder = inOrder(executionActionHandlerPort);
    inOrder
        .verify(executionActionHandlerPort)
        .afterExecutionFailedOnchain(same(intent), same(actionPlan), eq("RECEIPT_STATUS_0"));
    inOrder
        .verify(executionActionHandlerPort)
        .afterExecutionTerminated(
            same(intent),
            same(actionPlan),
            eq(ExecutionIntentStatus.FAILED_ONCHAIN),
            eq("RECEIPT_STATUS_0"));
  }

  @Test
  void execute_throwsWhenIntentDoesNotExist() {
    RunExecutionTerminationHookService service =
        new RunExecutionTerminationHookService(
            executionIntentPersistencePort, List.of(executionActionHandlerPort));

    when(executionIntentPersistencePort.findByPublicId("missing-intent"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.execute(
                    new RunExecutionTerminationHookCommand(
                        "missing-intent", ExecutionIntentStatus.EXPIRED, "reason")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("executionIntentId not found");
  }

  private ExecutionActionPlan actionPlan() {
    return new ExecutionActionPlan(
        BigInteger.valueOf(100),
        ExecutionReferenceType.USER_TO_USER,
        List.of(new ExecutionDraftCall("0x" + "3".repeat(40), BigInteger.ZERO, "0x1234")));
  }

  private ExecutionIntent existingEip1559Intent() {
    return ExecutionIntent.create(
        "intent-1",
        "root-1",
        1,
        ExecutionResourceType.TRANSFER,
        "transfer:1",
        ExecutionActionType.TRANSFER_SEND,
        7L,
        8L,
        ExecutionMode.EIP1559,
        "0x" + "a".repeat(64),
        "{\"amountWei\":\"100\"}",
        null,
        null,
        null,
        NOW.plusMinutes(5),
        null,
        null,
        new UnsignedTxSnapshot(
            11155111L,
            "0x" + "4".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.ZERO,
            "0x1234",
            5L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L)),
        "0x" + "b".repeat(64),
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 15),
        NOW);
  }
}
