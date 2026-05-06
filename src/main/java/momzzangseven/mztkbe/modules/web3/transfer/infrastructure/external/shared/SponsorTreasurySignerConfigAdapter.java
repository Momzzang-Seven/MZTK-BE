package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadSponsorTreasurySignerConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("transferSponsorTreasurySignerConfigAdapter")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class SponsorTreasurySignerConfigAdapter implements LoadSponsorTreasurySignerConfigPort {

  private final GetSponsorTreasurySignerConfigUseCase getSponsorTreasurySignerConfigUseCase;

  @Override
  public SponsorTreasurySignerConfig load() {
    var config = getSponsorTreasurySignerConfigUseCase.execute();
    return new SponsorTreasurySignerConfig(config.walletAlias());
  }
}
