package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.reservation;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CheckReservationExecutionCleanupProtectionUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceReservationCleanupProtectionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class MarketplaceReservationCleanupProtectionAdapter
    implements CheckMarketplaceReservationCleanupProtectionPort {

  private final CheckReservationExecutionCleanupProtectionUseCase
      checkReservationExecutionCleanupProtectionUseCase;

  @Override
  public List<String> findProtectedExecutionIntentPublicIds(
      List<MarketplaceExecutionCleanupIntent> intents) {
    return checkReservationExecutionCleanupProtectionUseCase.findProtectedExecutionIntentPublicIds(
        intents.stream().map(this::toReservationQuery).toList());
  }

  private ReservationExecutionCleanupProtectionQuery toReservationQuery(
      MarketplaceExecutionCleanupIntent intent) {
    return new ReservationExecutionCleanupProtectionQuery(
        intent.publicId(), intent.resourceId(), intent.actionType().name());
  }
}
