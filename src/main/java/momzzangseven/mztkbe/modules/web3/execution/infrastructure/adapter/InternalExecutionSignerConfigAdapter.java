package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.InternalExecutionIssuerProperties;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnInternalExecutionEnabled
public class InternalExecutionSignerConfigAdapter implements LoadInternalExecutionSignerConfigPort {

  private final InternalExecutionIssuerProperties internalExecutionIssuerProperties;

  public InternalExecutionSignerConfigAdapter(
      InternalExecutionIssuerProperties internalExecutionIssuerProperties) {
    this.internalExecutionIssuerProperties = internalExecutionIssuerProperties;
  }

  @Override
  public ExecutionSponsorWalletConfig loadSignerConfig() {
    var signer = internalExecutionIssuerProperties.getSigner();
    if (signer.getWalletAlias() == null || signer.getWalletAlias().isBlank()) {
      throw new Web3InvalidInputException(
          "web3.execution.internal-issuer.signer.wallet-alias is required");
    }
    if (signer.getKeyEncryptionKeyB64() == null || signer.getKeyEncryptionKeyB64().isBlank()) {
      throw new Web3InvalidInputException(
          "web3.execution.internal-issuer.signer.key-encryption-key-b64 is required");
    }
    return new ExecutionSponsorWalletConfig(
        signer.getWalletAlias(), signer.getKeyEncryptionKeyB64());
  }
}
