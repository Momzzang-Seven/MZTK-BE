package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionHookTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayTerminatedExecutionIntentServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private LoadExecutionTransactionPort loadExecutionTransactionPort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  @Mock private RunExecutionHookTransactionPort transactionPort;

  @Test
  void execute_replaysNonConfirmedTerminalHookInsideTransactionPort() {
    ExecutionIntent intent = intent(ExecutionIntentStatus.EXPIRED);
    ExecutionActionPlan actionPlan =
        new ExecutionActionPlan(BigInteger.ZERO, ExecutionReferenceType.SERVER_TO_USER, List.of());
    ExecutionTerminationEvidenceView evidence =
        ExecutionTerminationEvidenceView.unknown("intent-1");
    given(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .willReturn(java.util.Optional.of(intent));
    given(executionActionHandlerPort.supports(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(true);
    given(executionActionHandlerPort.supports(intent)).willReturn(true);
    given(executionActionHandlerPort.buildActionPlan(intent)).willReturn(actionPlan);
    given(
            executionActionHandlerPort.buildTerminationEvidence(
                intent, actionPlan, ExecutionIntentStatus.EXPIRED, "AUTH_EXPIRED"))
        .willReturn(evidence);
    doAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(transactionPort)
        .requiresNew(org.mockito.ArgumentMatchers.any());

    var service =
        new ReplayTerminatedExecutionIntentService(
            executionIntentPersistencePort,
            loadExecutionTransactionPort,
            List.of(executionActionHandlerPort),
            transactionPort,
            Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    boolean replayed =
        service.execute(
            new ReplayTerminatedExecutionIntentCommand("intent-1", "MARKETPLACE_ADMIN_REFUND"));

    assertThat(replayed).isTrue();
    verify(executionActionHandlerPort)
        .afterExecutionTerminated(
            intent, actionPlan, ExecutionIntentStatus.EXPIRED, "AUTH_EXPIRED", evidence);
  }

  @Test
  void execute_repairsFailedOnchainTransactionBeforeReplay() {
    ExecutionIntent intent =
        intent(ExecutionIntentStatus.PENDING_ONCHAIN).toBuilder().submittedTxId(77L).build();
    ExecutionIntent failed = intent.failOnchain("FAILED_ONCHAIN", "failed", LocalDateTime.now());
    ExecutionActionPlan actionPlan =
        new ExecutionActionPlan(BigInteger.ZERO, ExecutionReferenceType.SERVER_TO_USER, List.of());
    ExecutionTerminationEvidenceView evidence =
        ExecutionTerminationEvidenceView.unknown("intent-1");
    given(executionIntentPersistencePort.findByPublicIdForUpdate("intent-1"))
        .willReturn(java.util.Optional.of(intent));
    given(loadExecutionTransactionPort.findById(77L))
        .willReturn(
            java.util.Optional.of(
                new ExecutionTransactionSummary(
                    77L, ExecutionTransactionStatus.FAILED_ONCHAIN, "0x" + "a".repeat(64))));
    given(executionIntentPersistencePort.update(org.mockito.ArgumentMatchers.any()))
        .willReturn(failed);
    given(executionActionHandlerPort.supports(ExecutionActionType.MARKETPLACE_ADMIN_REFUND))
        .willReturn(true);
    given(executionActionHandlerPort.supports(failed)).willReturn(true);
    given(executionActionHandlerPort.buildActionPlan(failed)).willReturn(actionPlan);
    given(
            executionActionHandlerPort.buildTerminationEvidence(
                failed, actionPlan, ExecutionIntentStatus.FAILED_ONCHAIN, "FAILED_ONCHAIN"))
        .willReturn(evidence);
    doAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(transactionPort)
        .requiresNew(org.mockito.ArgumentMatchers.any());

    var service =
        new ReplayTerminatedExecutionIntentService(
            executionIntentPersistencePort,
            loadExecutionTransactionPort,
            List.of(executionActionHandlerPort),
            transactionPort,
            Clock.fixed(java.time.Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    boolean replayed =
        service.execute(
            new ReplayTerminatedExecutionIntentCommand("intent-1", "MARKETPLACE_ADMIN_REFUND"));

    assertThat(replayed).isTrue();
    verify(executionIntentPersistencePort).update(org.mockito.ArgumentMatchers.any());
    verify(executionActionHandlerPort)
        .afterExecutionTerminated(
            failed, actionPlan, ExecutionIntentStatus.FAILED_ONCHAIN, "FAILED_ONCHAIN", evidence);
  }

  private ExecutionIntent intent(ExecutionIntentStatus status) {
    return ExecutionIntent.builder()
        .publicId("intent-1")
        .rootIdempotencyKey("root")
        .attemptNo(1)
        .resourceType(ExecutionResourceType.ORDER)
        .resourceId("77")
        .actionType(ExecutionActionType.MARKETPLACE_ADMIN_REFUND)
        .requesterUserId(10L)
        .mode(ExecutionMode.EIP1559)
        .status(status)
        .payloadHash("hash")
        .payloadSnapshotJson("{}")
        .expiresAt(LocalDateTime.of(2026, 1, 1, 0, 0))
        .reservedSponsorCostWei(BigInteger.ZERO)
        .sponsorUsageDateKst(LocalDate.of(2026, 1, 1))
        .lastErrorCode("AUTH_EXPIRED")
        .build();
  }
}
