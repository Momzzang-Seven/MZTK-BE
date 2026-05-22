package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResolveExecutionIntentRecoveryTargetServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private LoadExecutionTransactionPort loadExecutionTransactionPort;

  private ResolveExecutionIntentRecoveryTargetService service;

  @BeforeEach
  void setUp() {
    service =
        new ResolveExecutionIntentRecoveryTargetService(
            executionIntentPersistencePort, loadExecutionTransactionPort);
  }

  @Test
  void execute_whenTransactionIdProvided_resolvesIntentBySubmittedTxId() {
    ExecutionIntent intent = intent().toBuilder().submittedTxId(24L).build();
    when(executionIntentPersistencePort.findBySubmittedTxId(24L)).thenReturn(Optional.of(intent));
    when(loadExecutionTransactionPort.findById(24L))
        .thenReturn(
            Optional.of(
                new ExecutionTransactionSummary(
                    24L, ExecutionTransactionStatus.SUCCEEDED, "0x" + "a".repeat(64))));

    var result =
        service
            .execute(ResolveExecutionIntentRecoveryTargetQuery.byTransactionId(24L))
            .orElseThrow();

    assertThat(result.executionIntentId()).isEqualTo("intent-1");
    assertThat(result.resourceType()).isEqualTo("WALLET_REGISTRATION");
    assertThat(result.resourceId()).isEqualTo("registration-1");
    assertThat(result.actionType()).isEqualTo("WALLET_ESCROW_APPROVE");
    assertThat(result.transactionStatus()).isEqualTo(ExecutionTransactionStatus.SUCCEEDED);
  }

  @Test
  void execute_whenRegistrationIdProvided_resolvesLatestWalletRegistrationIntent() {
    when(executionIntentPersistencePort.findLatestByResource(
            ExecutionResourceType.WALLET_REGISTRATION, "registration-1"))
        .thenReturn(Optional.of(intent()));

    var result =
        service
            .execute(ResolveExecutionIntentRecoveryTargetQuery.walletRegistration("registration-1"))
            .orElseThrow();

    assertThat(result.executionIntentStatus()).isEqualTo("PENDING_ONCHAIN");
  }

  private ExecutionIntent intent() {
    return ExecutionIntent.builder()
        .id(1L)
        .publicId("intent-1")
        .rootIdempotencyKey("root-1")
        .attemptNo(1)
        .resourceType(ExecutionResourceType.WALLET_REGISTRATION)
        .resourceId("registration-1")
        .actionType(ExecutionActionType.WALLET_ESCROW_APPROVE)
        .requesterUserId(7L)
        .mode(ExecutionMode.EIP7702)
        .status(ExecutionIntentStatus.PENDING_ONCHAIN)
        .payloadHash("payload-hash")
        .payloadSnapshotJson("{}")
        .authorityAddress("0x" + "1".repeat(40))
        .authorityNonce(1L)
        .delegateTarget("0x" + "2".repeat(40))
        .expiresAt(LocalDateTime.parse("2026-05-13T10:00:00"))
        .authorizationPayloadHash("0x" + "3".repeat(64))
        .executionDigest("0x" + "4".repeat(64))
        .sponsorUsageDateKst(LocalDate.parse("2026-05-13"))
        .createdAt(LocalDateTime.parse("2026-05-13T09:00:00"))
        .updatedAt(LocalDateTime.parse("2026-05-13T09:00:00"))
        .build();
  }
}
