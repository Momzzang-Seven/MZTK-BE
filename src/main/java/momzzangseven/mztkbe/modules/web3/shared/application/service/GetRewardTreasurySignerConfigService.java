package momzzangseven.mztkbe.modules.web3.shared.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySignerConfigView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetRewardTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadRewardTreasurySignerConfigSourcePort;

@RequiredArgsConstructor
public class GetRewardTreasurySignerConfigService implements GetRewardTreasurySignerConfigUseCase {

  private final LoadRewardTreasurySignerConfigSourcePort loadRewardTreasurySignerConfigSourcePort;

  @Override
  public TreasurySignerConfigView execute() {
    return loadRewardTreasurySignerConfigSourcePort.load();
  }
}
