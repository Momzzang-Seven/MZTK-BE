package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationCreateIdempotencyJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceReservationCleanupProtectionAdapter")
class MarketplaceReservationCleanupProtectionAdapterTest {

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
    MarketplaceReservationCleanupProtectionAdapter adapter =
        new MarketplaceReservationCleanupProtectionAdapter(
            reservationJpaRepository, createIdempotencyJpaRepository);

    List<String> result =
        adapter.findProtectedExecutionIntentPublicIds(
            List.of(
                intent(
                    "intent-current",
                    "10",
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE),
                intent(
                    "intent-create",
                    "20",
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE),
                intent(
                    "intent-orphan", "30", MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL),
                intent(
                    "intent-free",
                    "40",
                    MarketplaceExecutionActionType.MARKETPLACE_CLASS_CONFIRM)));

    assertThat(result).containsExactly("intent-current", "intent-create", "intent-orphan");
  }

  private MarketplaceExecutionCleanupIntent intent(
      String publicId, String resourceId, MarketplaceExecutionActionType actionType) {
    return new MarketplaceExecutionCleanupIntent(1L, publicId, resourceId, actionType, 7L);
  }
}
