package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

public interface ExecutionEip1559SigningPort {

  SignedTransaction sign(SignCommand command);

  /**
   * EIP-1559 sign command. The trailing {@link TreasurySigner} replaces the historical {@code
   * signerPrivateKeyHex} so plaintext key material never crosses module boundaries; the adapter
   * resolves the KMS-backed signature via the execution-local {@code SignDigestPort}.
   */
  record SignCommand(
      long chainId,
      long nonce,
      BigInteger gasLimit,
      String toAddress,
      BigInteger valueWei,
      String data,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      TreasurySigner signer) {

    /** Compact constructor — non-null guard for the signer capability handle. */
    public SignCommand {
      if (signer == null) {
        throw new Web3InvalidInputException("signer is required");
      }
    }
  }

  record SignedTransaction(String rawTransaction, String txHash) {}
}
