package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class ExecutionSponsorWalletConfigAdapter implements LoadExecutionSponsorWalletConfigPort {

  private final ExecutionEip7702Properties executionEip7702Properties;

  @Override
  public ExecutionSponsorWalletConfig loadSponsorWalletConfig() {
    var sponsor = executionEip7702Properties.getSponsor();
    return new ExecutionSponsorWalletConfig(
        sponsor.getWalletAlias(), sponsor.getKeyEncryptionKeyB64());
  }
}
