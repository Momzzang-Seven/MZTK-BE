package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ReplayTerminatedExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.RunExecutionHookTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayTerminatedExecutionIntentServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private ExecutionActionHandlerPort executionActionHandlerPort;
  @Mock private RunExecutionHookTransactionPort transactionPort;

  @Test
  void execute_replaysNonConfirmedTerminalHookInsideTransactionPort() {
    ExecutionIntent intent = intent(ExecutionIntentStatus.EXPIRED);
    ExecutionActionPlan actionPlan =
        new ExecutionActionPlan(BigInteger.ZERO, ExecutionReferenceType.SERVER_TO_USER, List.of());
    ExecutionTerminationEvidenceView evidence =
        ExecutionTerminationEvidenceView.unknown("intent-1");
    given(executionIntentPersistencePort.findByPublicId("intent-1"))
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
            executionIntentPersistencePort, List.of(executionActionHandlerPort), transactionPort);

    boolean replayed =
        service.execute(
            new ReplayTerminatedExecutionIntentCommand("intent-1", "MARKETPLACE_ADMIN_REFUND"));

    assertThat(replayed).isTrue();
    verify(executionActionHandlerPort)
        .afterExecutionTerminated(
            intent, actionPlan, ExecutionIntentStatus.EXPIRED, "AUTH_EXPIRED", evidence);
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
