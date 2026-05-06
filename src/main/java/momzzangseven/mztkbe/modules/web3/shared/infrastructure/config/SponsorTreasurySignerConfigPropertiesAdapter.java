package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySignerConfigView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadSponsorTreasurySignerConfigSourcePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorTreasurySignerConfigPropertiesAdapter
    implements LoadSponsorTreasurySignerConfigSourcePort {

  private final SponsorTreasurySignerProperties sponsorTreasurySignerProperties;

  @Override
  public TreasurySignerConfigView load() {
    return new TreasurySignerConfigView(sponsorTreasurySignerProperties.getWalletAlias());
  }
}
