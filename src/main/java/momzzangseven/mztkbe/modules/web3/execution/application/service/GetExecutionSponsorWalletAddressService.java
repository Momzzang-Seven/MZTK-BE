package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionSponsorKeyPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

@RequiredArgsConstructor
public class GetExecutionSponsorWalletAddressService
    implements GetExecutionSponsorWalletAddressUseCase {

  private final LoadInternalExecutionSignerConfigPort loadInternalExecutionSignerConfigPort;
  private final LoadExecutionSponsorKeyPort loadExecutionSponsorKeyPort;

  @Override
  public String execute() {
    var sponsorWalletConfig = loadInternalExecutionSignerConfigPort.loadSignerConfig();
    return loadExecutionSponsorKeyPort
        .loadByAlias(sponsorWalletConfig.walletAlias(), sponsorWalletConfig.keyEncryptionKeyB64())
        .map(LoadExecutionSponsorKeyPort.ExecutionSponsorKey::address)
        .map(address -> EvmAddress.of(address).value())
        .orElseThrow(() -> new Web3InvalidInputException("sponsor signer key is missing"));
  }
}
