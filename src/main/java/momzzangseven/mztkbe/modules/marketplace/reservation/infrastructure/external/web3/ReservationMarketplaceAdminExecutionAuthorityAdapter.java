package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionAuthorityStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.LoadMarketplaceAdminExecutionAuthorityUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
public class ReservationMarketplaceAdminExecutionAuthorityAdapter
    implements LoadMarketplaceAdminExecutionAuthorityPort {

  private final LoadMarketplaceAdminExecutionAuthorityUseCase
      loadMarketplaceAdminExecutionAuthorityUseCase;

  @Override
  public MarketplaceAdminExecutionAuthorityView load() {
    MarketplaceAdminExecutionAuthorityStatus status =
        loadMarketplaceAdminExecutionAuthorityUseCase.execute();
    return new MarketplaceAdminExecutionAuthorityView(
        status.requiresUserSignature(),
        status.authorityModel(),
        status.serverSignerAvailable(),
        status.serverSignerAddress(),
        status.relayerRegistered(),
        status.relayerRegistrationStatus(),
        false,
        false);
  }
}
