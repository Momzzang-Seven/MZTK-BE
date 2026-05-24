package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record VerifyUnbroadcastableAttemptCommand(
    long chainId, String fromAddress, long nonce, Long attemptId) {

  public VerifyUnbroadcastableAttemptCommand {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    fromAddress = EvmAddress.of(fromAddress).value();
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (attemptId == null || attemptId <= 0) {
      throw new Web3InvalidInputException("attemptId must be positive");
    }
  }
}
