package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetRewardTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadRewardTreasurySignerConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("transferRewardTreasurySignerConfigAdapter")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
public class RewardTreasurySignerConfigAdapter implements LoadRewardTreasurySignerConfigPort {

  private final GetRewardTreasurySignerConfigUseCase getRewardTreasurySignerConfigUseCase;

  @Override
  public RewardTreasurySignerConfig load() {
    var config = getRewardTreasurySignerConfigUseCase.execute();
    return new RewardTreasurySignerConfig(config.walletAlias(), config.keyEncryptionKeyB64());
  }
}
