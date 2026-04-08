package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transfer.SponsorDailyUsagePersistenceAdapter;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.ExecutionSponsorDailyUsageRecord;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.ManageExecutionSponsorDailyUsageUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SponsorDailyUsagePersistenceAdapterTest {

  @Mock private ManageExecutionSponsorDailyUsageUseCase manageExecutionSponsorDailyUsageUseCase;

  private SponsorDailyUsagePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SponsorDailyUsagePersistenceAdapter(manageExecutionSponsorDailyUsageUseCase);
  }

  @Test
  void getOrCreateForUpdate_delegatesAndMapsTransferUsage() {
    LocalDate usageDate = LocalDate.of(2026, 4, 6);
    LocalDateTime now = LocalDateTime.of(2026, 4, 8, 9, 0);
    when(manageExecutionSponsorDailyUsageUseCase.getOrCreateForUpdate(7L, usageDate))
        .thenReturn(
            new ExecutionSponsorDailyUsageRecord(
                1L, 7L, usageDate, BigInteger.TEN, BigInteger.ONE, now, now));

    SponsorDailyUsage usage = adapter.getOrCreateForUpdate(7L, usageDate);

    assertThat(usage.getId()).isEqualTo(1L);
    assertThat(usage.getUserId()).isEqualTo(7L);
    assertThat(usage.getReservedCostWei()).isEqualByComparingTo(BigInteger.TEN);
    assertThat(usage.getConsumedCostWei()).isEqualByComparingTo(BigInteger.ONE);
    assertThat(usage.getCreatedAt()).isEqualTo(now);
    assertThat(usage.getUpdatedAt()).isEqualTo(now);
    verify(manageExecutionSponsorDailyUsageUseCase).getOrCreateForUpdate(7L, usageDate);
  }

  @Test
  void find_mapsOptionalTransferUsage() {
    LocalDate usageDate = LocalDate.of(2026, 4, 6);
    when(manageExecutionSponsorDailyUsageUseCase.find(7L, usageDate))
        .thenReturn(
            Optional.of(
                new ExecutionSponsorDailyUsageRecord(
                    null, 7L, usageDate, BigInteger.ZERO, BigInteger.ZERO, null, null)));

    Optional<SponsorDailyUsage> usage = adapter.find(7L, usageDate);

    assertThat(usage).isPresent();
    assertThat(usage.orElseThrow().getUserId()).isEqualTo(7L);
    verify(manageExecutionSponsorDailyUsageUseCase).find(7L, usageDate);
  }
}
