package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import java.util.List;

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
      String sponsorPrivateKeyHex) {}

  record SignedPayload(String rawTx, String txHash) {}
}
