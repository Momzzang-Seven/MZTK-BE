package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.shared;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnInternalExecutionEnabled
public class InternalExecutionSignerConfigAdapter implements LoadInternalExecutionSignerConfigPort {

  private final GetSponsorTreasurySignerConfigUseCase getSponsorTreasurySignerConfigUseCase;

  public InternalExecutionSignerConfigAdapter(
      GetSponsorTreasurySignerConfigUseCase getSponsorTreasurySignerConfigUseCase) {
    this.getSponsorTreasurySignerConfigUseCase = getSponsorTreasurySignerConfigUseCase;
  }

  @Override
  public ExecutionSponsorWalletConfig loadSignerConfig() {
    var config = getSponsorTreasurySignerConfigUseCase.execute();
    if (config.walletAlias() == null || config.walletAlias().isBlank()) {
      throw new Web3InvalidInputException(
          "web3.execution.internal.signer.wallet-alias is required");
    }
    if (config.keyEncryptionKeyB64() == null || config.keyEncryptionKeyB64().isBlank()) {
      throw new Web3InvalidInputException(
          "web3.execution.internal.signer.key-encryption-key-b64 is required");
    }
    return new ExecutionSponsorWalletConfig(config.walletAlias(), config.keyEncryptionKeyB64());
  }
}
