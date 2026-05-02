package momzzangseven.mztkbe.modules.web3.eip7702.application.port.out;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

public interface Eip7702TransactionCodecPort {

  String encodeTransferData(String toAddress, BigInteger amountWei);

  String hashCalls(List<BatchCall> calls);

  String encodeExecute(List<BatchCall> calls, byte[] executionSignature);

  SignedPayload signAndEncode(SignCommand command);

  record BatchCall(String to, BigInteger value, byte[] data) {}

  record SignCommand(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<Eip7702ChainPort.AuthorizationTuple> authorizationList,
      TreasurySigner sponsorSigner) {

    public SignCommand {
      if (sponsorSigner == null) {
        throw new Web3InvalidInputException("sponsorSigner is required");
      }
    }
  }

  record SignedPayload(String rawTx, String txHash) {}
}
