package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record Eip7702AuthorizationPolicyResult(long minimumRemainingSeconds) {

  public Eip7702AuthorizationPolicyResult {
    if (minimumRemainingSeconds <= 0) {
      throw new Web3InvalidInputException("minimumRemainingSeconds must be positive");
    }
  }
}
