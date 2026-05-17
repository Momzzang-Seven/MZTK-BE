package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationCreateIdempotencyJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationExecutionCleanupProtectionPersistenceAdapter")
class ReservationExecutionCleanupProtectionPersistenceAdapterTest {

  @Mock private ReservationJpaRepository reservationJpaRepository;
  @Mock private ReservationCreateIdempotencyJpaRepository createIdempotencyJpaRepository;

  @Test
  @DisplayName(
      "reservation pointer, create idempotency pointer, unbound pending action intent를 보호한다")
  void findProtectedExecutionIntentPublicIds_returnsReferencedAndOrphanFallbackIntents() {
    given(reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(anyCollection()))
        .willReturn(List.of("intent-current"));
    given(
            createIdempotencyJpaRepository.findCurrentExecutionIntentPublicIdsIn(
                anyCollection(), anyCollection()))
        .willReturn(List.of("intent-create"));
    given(
            reservationJpaRepository.countUnboundPendingAction(
                anyLong(), anyCollection(), anyCollection()))
        .willReturn(0L);
    given(
            reservationJpaRepository.countUnboundPendingAction(
                eq(30L), anyCollection(), anyCollection()))
        .willReturn(1L);
    ReservationExecutionCleanupProtectionPersistenceAdapter adapter =
        new ReservationExecutionCleanupProtectionPersistenceAdapter(
            reservationJpaRepository, createIdempotencyJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(
            List.of(
                intent("intent-current", "10", "MARKETPLACE_CLASS_PURCHASE"),
                intent("intent-create", "20", "MARKETPLACE_CLASS_PURCHASE"),
                intent("intent-orphan", "30", "MARKETPLACE_CLASS_CANCEL"),
                intent("intent-free", "40", "MARKETPLACE_CLASS_CONFIRM")));

    assertThat(result).containsExactly("intent-current", "intent-create", "intent-orphan");
  }

  private ReservationExecutionCleanupProtectionQuery intent(
      String publicId, String resourceId, String actionType) {
    return new ReservationExecutionCleanupProtectionQuery(publicId, resourceId, actionType);
  }
}
