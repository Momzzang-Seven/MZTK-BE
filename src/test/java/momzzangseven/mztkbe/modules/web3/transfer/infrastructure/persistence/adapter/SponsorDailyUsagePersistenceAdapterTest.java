package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3SponsorDailyUsageEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.Web3SponsorDailyUsageJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SponsorDailyUsagePersistenceAdapterTest {

  @Mock private Web3SponsorDailyUsageJpaRepository repository;

  private SponsorDailyUsagePersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SponsorDailyUsagePersistenceAdapter(repository);
  }

  @Test
  void create_throws_whenIdPresent() {
    SponsorDailyUsage usage =
        SponsorDailyUsage.builder()
            .id(1L)
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .reservedCostWei(BigInteger.ZERO)
            .consumedCostWei(BigInteger.ONE)
            .build();

    assertThatThrownBy(() -> adapter.create(usage))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("create requires id to be null");
  }

  @Test
  void update_throws_whenIdMissing() {
    SponsorDailyUsage usage =
        SponsorDailyUsage.builder()
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .reservedCostWei(BigInteger.ZERO)
            .consumedCostWei(BigInteger.ONE)
            .build();

    assertThatThrownBy(() -> adapter.update(usage))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("update requires id");
  }

  @Test
  void create_mapsAndReturnsDomain() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    SponsorDailyUsage usage =
        SponsorDailyUsage.builder()
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .reservedCostWei(BigInteger.ONE)
            .consumedCostWei(BigInteger.TEN)
            .build();

    SponsorDailyUsage created = adapter.create(usage);

    ArgumentCaptor<Web3SponsorDailyUsageEntity> captor =
        ArgumentCaptor.forClass(Web3SponsorDailyUsageEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(7L);
    assertThat(captor.getValue().getReservedCostWei()).isEqualTo(BigInteger.ONE);
    assertThat(created.getConsumedCostWei()).isEqualTo(BigInteger.TEN);
  }

  @Test
  void findUsageIdsForCleanup_delegatesWithBatchSize() {
    when(repository.findUsageIdsForCleanup(eq(LocalDate.of(2026, 2, 1)), any()))
        .thenReturn(List.of(1L, 2L));

    List<Long> ids = adapter.findUsageIdsForCleanup(LocalDate.of(2026, 2, 1), 50);

    assertThat(ids).containsExactly(1L, 2L);
  }
}
