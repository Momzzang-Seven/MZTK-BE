package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferRuntimeConfigPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.TransferPreparePersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferRuntimeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Eip7702CleanupServiceTest {

  @Mock private TransferPreparePersistencePort transferPreparePersistencePort;
  @Mock private SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  @Mock private LoadTransferRuntimeConfigPort loadTransferRuntimeConfigPort;

  private Eip7702CleanupService service;

  @BeforeEach
  void setUp() {
    service =
        new Eip7702CleanupService(
            transferPreparePersistencePort,
            sponsorDailyUsagePersistencePort,
            loadTransferRuntimeConfigPort);
  }

  @Test
  void runBatch_returnsZero_whenNothingToDelete() {
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findPrepareIdsForCleanup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of());
    when(sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of());

    Eip7702CleanupService.CleanupBatchResult result =
        service.runBatch(Instant.parse("2026-03-01T00:00:00Z"));

    assertThat(result.deletedPrepare()).isZero();
    assertThat(result.deletedDailyUsage()).isZero();
    assertThat(result.totalDeleted()).isZero();
  }

  @Test
  void runBatch_deletesPrepareAndUsageRows() {
    when(loadTransferRuntimeConfigPort.load()).thenReturn(runtimeConfig());
    when(transferPreparePersistencePort.findPrepareIdsForCleanup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of("p1", "p2"));
    when(sponsorDailyUsagePersistencePort.findUsageIdsForCleanup(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100)))
        .thenReturn(List.of(1L));
    when(transferPreparePersistencePort.deleteByPrepareIdIn(List.of("p1", "p2"))).thenReturn(2L);
    when(sponsorDailyUsagePersistencePort.deleteByIdIn(List.of(1L))).thenReturn(1L);

    Eip7702CleanupService.CleanupBatchResult result =
        service.runBatch(Instant.parse("2026-03-01T00:00:00Z"));

    assertThat(result.deletedPrepare()).isEqualTo(2);
    assertThat(result.deletedDailyUsage()).isEqualTo(1);
    assertThat(result.totalDeleted()).isEqualTo(3);
    verify(transferPreparePersistencePort).deleteByPrepareIdIn(List.of("p1", "p2"));
    verify(sponsorDailyUsagePersistencePort).deleteByIdIn(List.of(1L));
  }

  private TransferRuntimeConfig runtimeConfig() {
    return new TransferRuntimeConfig(
        11155111L,
        "0x" + "a".repeat(40),
        30,
        "0x" + "b".repeat(40),
        "0x" + "c".repeat(40),
        "sponsor",
        "kek",
        1_000_000L,
        new BigDecimal("0.05"),
        new BigDecimal("0.01"),
        new BigDecimal("0.02"),
        600,
        "Asia/Seoul",
        7,
        100);
  }
}
