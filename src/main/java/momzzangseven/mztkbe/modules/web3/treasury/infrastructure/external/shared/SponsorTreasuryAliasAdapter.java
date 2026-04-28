package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.external.shared;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadSponsorTreasuryAliasPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SponsorTreasuryAliasAdapter implements LoadSponsorTreasuryAliasPort {

  private final GetSponsorTreasurySignerConfigUseCase getSponsorTreasurySignerConfigUseCase;

  @Override
  public Optional<String> loadAlias() {
    return Optional.ofNullable(getSponsorTreasurySignerConfigUseCase.execute().walletAlias());
  }
}
