package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayConfirmedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayConfirmedExecutionIntentServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private LoadExecutionTransactionPort loadExecutionTransactionPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  private static final Clock FIXED_CLOCK =
      Clock.fixed(java.time.Instant.parse("2026-04-12T01:03:00Z"), ZoneId.of("Asia/Seoul"));

  @Test
  void execute_replaysConfirmedIntentWhenActionTypeMatches() {
    ExecutionIntent intent = confirmedIntent(ExecutionActionType.QNA_QUESTION_UPDATE);
    ExecutionActionPlan actionPlan = actionPlan();
    ReplayConfirmedExecutionIntentService service = service();
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(executionActionHandlerPort.supports(ExecutionActionType.QNA_QUESTION_UPDATE))
        .thenReturn(true);
    when(executionActionHandlerPort.supports(intent)).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(intent)).thenReturn(actionPlan);

    boolean result =
        service.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));

    assertThat(result).isTrue();
    verify(executionActionHandlerPort).afterExecutionConfirmed(same(intent), same(actionPlan));
  }

  @Test
  void execute_skipsWhenIntentActionTypeDoesNotMatch() {
    ExecutionIntent intent = confirmedIntent(ExecutionActionType.QNA_QUESTION_DELETE);
    ReplayConfirmedExecutionIntentService service = service();
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));

    boolean result =
        service.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));

    assertThat(result).isFalse();
    verify(executionActionHandlerPort, never()).afterExecutionConfirmed(any(), any());
  }

  @Test
  void execute_skipsWhenIntentIsNotConfirmed() {
    ExecutionIntent intent = pendingIntent(ExecutionActionType.QNA_QUESTION_UPDATE);
    ReplayConfirmedExecutionIntentService service = service();
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));

    boolean result =
        service.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));

    assertThat(result).isFalse();
    verify(executionActionHandlerPort, never()).afterExecutionConfirmed(any(), any());
  }

  @Test
  void execute_repairsPendingIntentWhenSubmittedTransactionSucceededThenReplays() {
    ExecutionIntent intent = pendingIntent(ExecutionActionType.QNA_QUESTION_UPDATE);
    ExecutionActionPlan actionPlan = actionPlan();
    ReplayConfirmedExecutionIntentService service = service();
    when(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .thenReturn(Optional.of(intent));
    when(loadExecutionTransactionPort.findById(99L))
        .thenReturn(
            Optional.of(
                new ExecutionTransactionSummary(
                    99L, ExecutionTransactionStatus.SUCCEEDED, "0xhash")));
    when(executionIntentPersistencePort.update(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, ExecutionIntent.class));
    when(executionActionHandlerPort.supports(ExecutionActionType.QNA_QUESTION_UPDATE))
        .thenReturn(true);
    when(executionActionHandlerPort.supports(any(ExecutionIntent.class))).thenReturn(true);
    when(executionActionHandlerPort.buildActionPlan(any(ExecutionIntent.class)))
        .thenReturn(actionPlan);

    boolean result =
        service.execute(
            new ReplayConfirmedExecutionIntentCommand("intent-1", "QNA_QUESTION_UPDATE"));

    assertThat(result).isTrue();
    verify(executionIntentPersistencePort).update(any(ExecutionIntent.class));
    verify(executionActionHandlerPort)
        .afterExecutionConfirmed(any(ExecutionIntent.class), same(actionPlan));
  }

  private ReplayConfirmedExecutionIntentService service() {
    return new ReplayConfirmedExecutionIntentService(
        executionIntentPersistencePort,
        loadExecutionTransactionPort,
        List.of(executionActionHandlerPort),
        FIXED_CLOCK);
  }

  private ExecutionActionPlan actionPlan() {
    return new ExecutionActionPlan(
        BigInteger.ZERO,
        ExecutionReferenceType.USER_TO_SERVER,
        List.of(new ExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0x1234")));
  }

  private ExecutionIntent confirmedIntent(ExecutionActionType actionType) {
    return pendingIntent(actionType).confirm(LocalDateTime.of(2026, 4, 12, 10, 2));
  }

  private ExecutionIntent pendingIntent(ExecutionActionType actionType) {
    return ExecutionIntent.create(
            "intent-1",
            "root-1",
            1,
            ExecutionResourceType.QUESTION,
            "101",
            actionType,
            7L,
            null,
            ExecutionMode.EIP7702,
            "0x" + "e".repeat(64),
            "{}",
            "0x" + "1".repeat(40),
            1L,
            "0x" + "2".repeat(40),
            LocalDateTime.of(2026, 4, 12, 10, 5),
            "0x" + "3".repeat(64),
            "0x" + "4".repeat(64),
            null,
            null,
            BigInteger.ZERO,
            LocalDate.of(2026, 4, 12),
            LocalDateTime.of(2026, 4, 12, 10, 0))
        .toBuilder()
        .submittedTxId(99L)
        .build()
        .markPendingOnchain(99L, LocalDateTime.of(2026, 4, 12, 10, 1));
  }
}
