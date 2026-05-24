package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record GetSponsorNonceSlotsQuery(Long operatorId, Long chainId, String fromAddress) {

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (chainId == null || chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    EvmAddress.of(fromAddress);
  }

  public String normalizedFromAddress() {
    return EvmAddress.of(fromAddress).value();
  }
}
