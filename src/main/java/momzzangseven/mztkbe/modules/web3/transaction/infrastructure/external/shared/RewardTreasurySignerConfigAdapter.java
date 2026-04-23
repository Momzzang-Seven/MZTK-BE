package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetRewardTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.LoadRewardTreasurySignerConfigPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("transactionRewardTreasurySignerConfigAdapter")
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
