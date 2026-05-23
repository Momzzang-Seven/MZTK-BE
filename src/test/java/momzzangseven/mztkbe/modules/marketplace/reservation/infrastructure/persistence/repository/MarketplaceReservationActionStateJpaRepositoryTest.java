package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class MarketplaceReservationActionStateJpaRepositoryTest {

  @Test
  @DisplayName("expired admin preparation claim query는 FOR UPDATE SKIP LOCKED를 사용한다")
  void findExpiredAdminPreparingAttemptsWithLockUsesSkipLocked() throws Exception {
    Method method =
        MarketplaceReservationActionStateJpaRepository.class.getDeclaredMethod(
            "findExpiredAdminPreparingAttemptsWithLock", LocalDateTime.class, int.class);
    Query query = method.getAnnotation(Query.class);

    assertThat(query).isNotNull();
    assertThat(query.nativeQuery()).isTrue();
    assertThat(query.value().toLowerCase()).contains("for update skip locked");
  }

  @Test
  @DisplayName(
      "admin reconciliation 후보 query는 terminal intent와 repairable transaction outcome을 포함한다")
  void findBoundAdminExecutionAttemptsForTerminalReplayIncludesRepairableTransactionOutcomes()
      throws Exception {
    Method method =
        MarketplaceReservationActionStateJpaRepository.class.getDeclaredMethod(
            "findBoundAdminExecutionAttemptsForTerminalReplay", LocalDateTime.class, int.class);
    Query query = method.getAnnotation(Query.class);

    assertThat(query).isNotNull();
    assertThat(query.nativeQuery()).isTrue();
    assertThat(query.value().toLowerCase()).contains("from web3_execution_intents");
    assertThat(query.value().toLowerCase()).contains("left join web3_transactions");
    assertThat(query.value().toLowerCase()).contains("for update skip locked");
    assertThat(query.value()).contains("'CONFIRMED'");
    assertThat(query.value()).contains("'SUCCEEDED'");
    assertThat(query.value()).contains("'FAILED_ONCHAIN'");
    assertThat(query.value()).contains("'SIGNED'");
    assertThat(query.value()).contains("'PENDING_ONCHAIN'");
  }
}
