package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadExecutionSignerConfigPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnInternalExecutionEnabled
public class ExecutionSignerConfigPropertiesAdapter implements LoadExecutionSignerConfigPort {

  private final SponsorTreasurySignerProperties sponsorTreasurySignerProperties;

  @Override
  public ExecutionSignerConfig load() {
    return new ExecutionSignerConfig(
        sponsorTreasurySignerProperties.getWalletAlias(),
        sponsorTreasurySignerProperties.getKeyEncryptionKeyB64());
  }
}
