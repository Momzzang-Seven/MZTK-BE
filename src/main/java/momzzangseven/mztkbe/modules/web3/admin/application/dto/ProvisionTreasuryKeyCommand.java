package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;

public record ProvisionTreasuryKeyCommand(
    Long operatorId, String rawPrivateKey, TreasuryRole role, String expectedAddress) {

  public void validate() {
    if (operatorId == null || operatorId <= 0) {
      throw new Web3InvalidInputException("operatorId must be positive");
    }
    if (rawPrivateKey == null || rawPrivateKey.isBlank()) {
      throw new Web3InvalidInputException("rawPrivateKey is required");
    }
    if (role == null) {
      throw new Web3InvalidInputException("role is required");
    }
    if (expectedAddress == null || expectedAddress.isBlank()) {
      throw new Web3InvalidInputException("expectedAddress is required");
    }
  }
}
