package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import java.util.Map;

/** Port for on-chain contract operations used by reward token workers. */
public interface Web3ContractPort {

  PrevalidateResult prevalidate(PrevalidateCommand command);

  SignedTransaction signTransfer(SignTransferCommand command);

  BroadcastResult broadcast(BroadcastCommand command);

  ReceiptResult getReceipt(String txHash);

  record PrevalidateCommand(String fromAddress, String toAddress, BigInteger amountWei) {}

  record PrevalidateResult(
      boolean ok,
      boolean retryable,
      String failureReason,
      BigInteger gasLimit,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      Map<String, Object> detail) {}

  record SignTransferCommand(
      String treasuryPrivateKeyHex,
      String tokenContractAddress,
      String toAddress,
      BigInteger amountWei,
      long nonce,
      long chainId,
      BigInteger gasLimit,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas) {}

  record SignedTransaction(String rawTx, String txHash) {}

  record BroadcastCommand(String rawTx) {}

  record BroadcastResult(boolean success, String txHash, String failureReason, String rpcAlias) {}

  record ReceiptResult(
      String txHash,
      boolean found,
      Boolean success,
      String rpcAlias,
      boolean rpcError,
      String failureReason) {}
}
