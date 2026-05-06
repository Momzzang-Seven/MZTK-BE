package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionSponsorWalletAddressUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

@RequiredArgsConstructor
public class GetExecutionSponsorWalletAddressService
    implements GetExecutionSponsorWalletAddressUseCase {

  private final LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;

  @Override
  public String execute() {
    return loadSponsorTreasuryWalletPort
        .load()
        .map(TreasuryWalletInfo::walletAddress)
        .map(address -> EvmAddress.of(address).value())
        .orElseThrow(() -> new Web3InvalidInputException("sponsor signer key is missing"));
  }
}
