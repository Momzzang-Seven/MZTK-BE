package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

public record Eip7702ExecutionSignCommand(
    long chainId,
    BigInteger nonce,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas,
    BigInteger gasLimit,
    String to,
    BigInteger value,
    String data,
    List<Eip7702ExecutionAuthorizationTuple> authorizationList,
    TreasurySigner sponsorSigner) {

  public Eip7702ExecutionSignCommand {
    if (sponsorSigner == null) {
      throw new Web3InvalidInputException("sponsorSigner is required");
    }
  }
}
