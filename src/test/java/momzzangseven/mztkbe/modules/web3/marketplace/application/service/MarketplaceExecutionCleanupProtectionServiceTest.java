package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceReservationCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceExecutionCleanupIntentPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceExecutionCleanupProtectionService")
class MarketplaceExecutionCleanupProtectionServiceTest {

  @Mock private LoadMarketplaceExecutionCleanupIntentPort loadIntentPort;
  @Mock private CheckMarketplaceReservationCleanupProtectionPort reservationProtectionPort;

  @InjectMocks private MarketplaceExecutionCleanupProtectionService service;

  @Test
  @DisplayName("reservation/idempotency/orphan evidence가 남은 marketplace intent는 삭제 후보에서 제외한다")
  void filterDeletableFinalizedIntentIds_filtersProtectedMarketplaceIntents() {
    MarketplaceExecutionCleanupIntent protectedIntent =
        intent(1L, "intent-protected", MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE);
    MarketplaceExecutionCleanupIntent deletableIntent =
        intent(2L, "intent-free", MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL);
    given(loadIntentPort.loadByIds(List.of(1L, 2L)))
        .willReturn(List.of(protectedIntent, deletableIntent));
    given(
            reservationProtectionPort.findProtectedExecutionIntentPublicIds(
                List.of(protectedIntent, deletableIntent)))
        .willReturn(List.of("intent-protected"));

    List<Long> result = service.filterDeletableFinalizedIntentIds(List.of(1L, 2L));

    assertThat(result).containsExactly(2L);
  }

  private MarketplaceExecutionCleanupIntent intent(
      Long id, String publicId, MarketplaceExecutionActionType actionType) {
    return new MarketplaceExecutionCleanupIntent(id, publicId, "10", actionType, 1L);
  }
}
