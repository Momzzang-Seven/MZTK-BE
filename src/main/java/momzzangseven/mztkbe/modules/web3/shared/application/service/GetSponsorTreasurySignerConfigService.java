package momzzangseven.mztkbe.modules.web3.shared.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySignerConfigView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadSponsorTreasurySignerConfigSourcePort;

@RequiredArgsConstructor
public class GetSponsorTreasurySignerConfigService
    implements GetSponsorTreasurySignerConfigUseCase {

  private final LoadSponsorTreasurySignerConfigSourcePort loadSponsorTreasurySignerConfigSourcePort;

  @Override
  public TreasurySignerConfigView execute() {
    return loadSponsorTreasurySignerConfigSourcePort.load();
  }
}
