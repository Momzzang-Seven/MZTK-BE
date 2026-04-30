package momzzangseven.mztkbe.modules.web3.token.infrastructure.external.shared;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetRewardTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadRewardTreasuryAliasPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RewardTreasuryAliasAdapter implements LoadRewardTreasuryAliasPort {

  private final GetRewardTreasurySignerConfigUseCase getRewardTreasurySignerConfigUseCase;

  @Override
  public Optional<String> loadAlias() {
    return Optional.ofNullable(getRewardTreasurySignerConfigUseCase.execute().walletAlias());
  }
}
