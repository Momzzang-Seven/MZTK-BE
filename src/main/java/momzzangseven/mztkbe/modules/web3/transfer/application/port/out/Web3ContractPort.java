package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/** Port for on-chain contract operations used by reward token workers. */
public interface Web3ContractPort {

  PrevalidateResult prevalidate(PrevalidateCommand command);

  SignedTransaction signTransfer(SignTransferCommand command);

  BroadcastResult broadcast(BroadcastCommand command);

  ReceiptResult getReceipt(String txHash);

  record PrevalidateCommand(String fromAddress, String toAddress, BigInteger amountWei) {

    public PrevalidateCommand {
      validate(fromAddress, toAddress, amountWei);
    }

    private static void validate(String fromAddress, String toAddress, BigInteger amountWei) {
      EvmAddress.of(fromAddress);
      EvmAddress.of(toAddress);
      if (amountWei == null || amountWei.signum() < 0) {
        throw new Web3InvalidInputException("amountWei must be non-negative");
      }
    }
  }

  record PrevalidateResult(
      boolean ok,
      boolean retryable,
      String failureReason,
      BigInteger gasLimit,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      Map<String, Object> detail) {

    public PrevalidateResult {
      if (detail == null) {
        detail = Collections.emptyMap();
      }
      validate(ok, gasLimit, maxPriorityFeePerGas, maxFeePerGas, detail);
    }

    private static void validate(
        boolean ok,
        BigInteger gasLimit,
        BigInteger maxPriorityFeePerGas,
        BigInteger maxFeePerGas,
        Map<String, Object> detail) {
      if (ok) {
        if (gasLimit == null || gasLimit.signum() <= 0) {
          throw new Web3InvalidInputException("gasLimit must be > 0 when prevalidate succeeds");
        }
        if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() <= 0) {
          throw new Web3InvalidInputException(
              "maxPriorityFeePerGas must be > 0 when prevalidate succeeds");
        }
        if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
          throw new Web3InvalidInputException("maxFeePerGas must be > 0 when prevalidate succeeds");
        }
      }
      if (detail == null) {
        throw new Web3InvalidInputException("detail is required");
      }
    }
  }

  record SignTransferCommand(
      String treasuryPrivateKeyHex,
      String tokenContractAddress,
      String toAddress,
      BigInteger amountWei,
      long nonce,
      long chainId,
      BigInteger gasLimit,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas) {

    public SignTransferCommand {}
  }

  record SignedTransaction(String rawTx, String txHash) {

    public SignedTransaction {
      if (rawTx == null || rawTx.isBlank()) {
        throw new Web3InvalidInputException("rawTx is required");
      }
      if (txHash == null || txHash.isBlank()) {
        throw new Web3InvalidInputException("txHash is required");
      }
    }
  }

  record BroadcastCommand(String rawTx) {

    public BroadcastCommand {
      if (rawTx == null || rawTx.isBlank()) {
        throw new Web3InvalidInputException("rawTx is required");
      }
    }
  }

  record BroadcastResult(boolean success, String txHash, String failureReason, String rpcAlias) {

    public BroadcastResult {}
  }

  record ReceiptResult(
      String txHash,
      boolean found,
      Boolean success,
      String rpcAlias,
      boolean rpcError,
      String failureReason) {

    public ReceiptResult {
      if (txHash == null || txHash.isBlank()) {
        throw new Web3InvalidInputException("txHash is required");
      }
      if (rpcError && (failureReason == null || failureReason.isBlank())) {
        throw new Web3InvalidInputException("failureReason is required when rpcError is true");
      }
    }
  }
}
