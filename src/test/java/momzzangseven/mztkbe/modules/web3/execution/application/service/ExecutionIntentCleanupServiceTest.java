package momzzangseven.mztkbe.modules.web3.execution.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionCleanupPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionCleanupPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionIntentCleanupServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private LoadExecutionCleanupPolicyPort loadExecutionCleanupPolicyPort;
  @Mock private PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;

  private ExecutionIntentCleanupService service;

  @BeforeEach
  void setUp() {
    service =
        new ExecutionIntentCleanupService(
            executionIntentPersistencePort,
            sponsorDailyUsagePersistencePort,
            loadExecutionCleanupPolicyPort,
            publishExecutionIntentTerminatedPort);
  }

  @Test
  void runBatch_returnsZero_whenNothingToDelete() {
    when(loadExecutionCleanupPolicyPort.loadCleanupPolicy()).thenReturn(cleanupPolicy());
    when(executionIntentPersistencePort.findExpiredAwaitingSignatureIds(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of());
    when(executionIntentPersistencePort.findRetainedFinalizedIds(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of());
    when(sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of());

    ExecutionIntentCleanupService.CleanupBatchResult result =
        service.runBatch(Instant.parse("2026-03-01T00:00:00Z"));

    assertThat(result.expiredExecutionIntent()).isZero();
    assertThat(result.deletedExecutionIntent()).isZero();
    assertThat(result.deletedDailyUsage()).isZero();
    assertThat(result.totalDeleted()).isZero();
  }

  @Test
  void runBatch_expiresExpiredIntent_deletesRetainedIntentAndUsageRows() {
    when(loadExecutionCleanupPolicyPort.loadCleanupPolicy()).thenReturn(cleanupPolicy());
    when(executionIntentPersistencePort.findExpiredAwaitingSignatureIds(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of(10L));
    when(executionIntentPersistencePort.findAllByIdsForUpdate(List.of(10L)))
        .thenReturn(List.of(expiredIntent()));
    when(executionIntentPersistencePort.update(
            org.mockito.ArgumentMatchers.any(ExecutionIntent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(sponsorDailyUsagePersistencePort.findForUpdate(7L, LocalDate.of(2026, 2, 28)))
        .thenReturn(
            java.util.Optional.of(
                SponsorDailyUsage.builder()
                    .id(1L)
                    .userId(7L)
                    .usageDateKst(LocalDate.of(2026, 2, 28))
                    .reservedCostWei(new BigInteger("120"))
                    .consumedCostWei(BigInteger.ZERO)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build()));
    when(sponsorDailyUsagePersistencePort.update(
            org.mockito.ArgumentMatchers.any(SponsorDailyUsage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(executionIntentPersistencePort.findRetainedFinalizedIds(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of(11L, 12L));
    when(sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of(1L));
    when(executionIntentPersistencePort.deleteByIds(List.of(11L, 12L))).thenReturn(2L);
    when(sponsorDailyUsagePersistencePort.deleteByIdIn(List.of(1L))).thenReturn(1L);

    ExecutionIntentCleanupService.CleanupBatchResult result =
        service.runBatch(Instant.parse("2026-03-01T00:00:00Z"));

    assertThat(result.expiredExecutionIntent()).isEqualTo(1);
    assertThat(result.deletedExecutionIntent()).isEqualTo(2);
    assertThat(result.deletedDailyUsage()).isEqualTo(1);
    assertThat(result.totalDeleted()).isEqualTo(4);
    verify(executionIntentPersistencePort).deleteByIds(List.of(11L, 12L));
    verify(sponsorDailyUsagePersistencePort).deleteByIdIn(List.of(1L));
    verify(publishExecutionIntentTerminatedPort)
        .publish(
            argThat(
                event ->
                    event.executionIntentId().equals("intent-10")
                        && event.terminalStatus() == ExecutionIntentStatus.EXPIRED));
  }

  private ExecutionIntent expiredIntent() {
    return ExecutionIntent.builder()
        .id(10L)
        .publicId("intent-10")
        .rootIdempotencyKey("root")
        .attemptNo(1)
        .resourceType(ExecutionResourceType.TRANSFER)
        .resourceId("web3:TRANSFER_SEND:7:req-101")
        .actionType(ExecutionActionType.TRANSFER_SEND)
        .requesterUserId(7L)
        .counterpartyUserId(22L)
        .mode(ExecutionMode.EIP7702)
        .status(ExecutionIntentStatus.AWAITING_SIGNATURE)
        .payloadHash("0x" + "a".repeat(64))
        .payloadSnapshotJson("{}")
        .authorityAddress("0x" + "1".repeat(40))
        .authorityNonce(1L)
        .delegateTarget("0x" + "2".repeat(40))
        .expiresAt(LocalDateTime.of(2026, 2, 28, 0, 0))
        .authorizationPayloadHash("0x" + "b".repeat(64))
        .executionDigest("0x" + "c".repeat(64))
        .reservedSponsorCostWei(new BigInteger("120"))
        .sponsorUsageDateKst(LocalDate.of(2026, 2, 28))
        .createdAt(LocalDateTime.of(2026, 2, 28, 0, 0))
        .updatedAt(LocalDateTime.of(2026, 2, 28, 0, 0))
        .build();
  }

  private ExecutionCleanupPolicy cleanupPolicy() {
    return new ExecutionCleanupPolicy("Asia/Seoul", 7, 100);
  }
}
