package momzzangseven.mztkbe.modules.web3.execution.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SponsorDailyUsagePersistenceAdapterTest {

  @Mock private Web3SponsorDailyUsageJpaRepository repository;

  private SponsorDailyUsagePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SponsorDailyUsagePersistenceAdapter(repository);
  }

  @Test
  void getOrCreateForUpdate_createsRowWhenMissing() {
    LocalDate usageDate = LocalDate.of(2026, 4, 6);
    when(repository.findForUpdate(7L, usageDate))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(entity(1L, 7L, usageDate)));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    SponsorDailyUsage usage = adapter.getOrCreateForUpdate(7L, usageDate);

    assertThat(usage.getUserId()).isEqualTo(7L);
    assertThat(usage.getUsageDateKst()).isEqualTo(usageDate);
    verify(repository).save(any());
    verify(repository, times(2)).findForUpdate(7L, usageDate);
  }

  @Test
  void getOrCreateForUpdate_refetchesWhenConcurrentInsertWins() {
    LocalDate usageDate = LocalDate.of(2026, 4, 6);
    when(repository.findForUpdate(7L, usageDate))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(entity(2L, 7L, usageDate)));
    when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

    SponsorDailyUsage usage = adapter.getOrCreateForUpdate(7L, usageDate);

    assertThat(usage.getId()).isEqualTo(2L);
    assertThat(usage.getConsumedCostWei()).isEqualByComparingTo(BigInteger.ZERO);
    verify(repository).save(any());
    verify(repository, times(2)).findForUpdate(7L, usageDate);
  }

  private Web3SponsorDailyUsageEntity entity(Long id, Long userId, LocalDate usageDateKst) {
    return Web3SponsorDailyUsageEntity.builder()
        .id(id)
        .userId(userId)
        .usageDateKst(usageDateKst)
        .reservedCostWei(BigInteger.ZERO)
        .consumedCostWei(BigInteger.ZERO)
        .build();
  }
}
