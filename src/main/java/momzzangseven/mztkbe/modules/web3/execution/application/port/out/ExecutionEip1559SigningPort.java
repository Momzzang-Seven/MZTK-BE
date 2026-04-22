package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.math.BigInteger;

public interface ExecutionEip1559SigningPort {

  SignedTransaction sign(SignCommand command);

  record SignCommand(
      long chainId,
      long nonce,
      BigInteger gasLimit,
      String toAddress,
      BigInteger valueWei,
      String data,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      String signerPrivateKeyHex) {}

  record SignedTransaction(String rawTransaction, String txHash) {}
}
