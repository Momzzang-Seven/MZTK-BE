package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorWalletConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionSponsorWalletConfigAdapter implements LoadExecutionSponsorWalletConfigPort {

  private final TransferRuntimeConfigAdapter transferRuntimeConfigAdapter;

  @Override
  public ExecutionSponsorWalletConfig loadSponsorWalletConfig() {
    var config = transferRuntimeConfigAdapter.load();
    return new ExecutionSponsorWalletConfig(
        config.sponsorWalletAlias(), config.sponsorKeyEncryptionKeyB64());
  }
}
