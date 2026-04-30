package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.shared;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class ExecutionSponsorWalletConfigAdapter implements LoadExecutionSponsorWalletConfigPort {

  private final GetSponsorTreasurySignerConfigUseCase getSponsorTreasurySignerConfigUseCase;

  @Override
  public ExecutionSponsorWalletConfig loadSponsorWalletConfig() {
    var config = getSponsorTreasurySignerConfigUseCase.execute();
    return new ExecutionSponsorWalletConfig(config.walletAlias(), config.keyEncryptionKeyB64());
  }
}
