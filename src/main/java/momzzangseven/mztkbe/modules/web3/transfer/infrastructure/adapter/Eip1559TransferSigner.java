package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Web3ContractPort;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

/** Stateless signer for ERC-20 transfer EIP-1559 transaction payloads. */
public final class Eip1559TransferSigner {

  private Eip1559TransferSigner() {}

  public static String encodeTransferData(String toAddress, BigInteger amountWei) {
    EvmAddress normalizedToAddress = EvmAddress.of(toAddress);
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException("amountWei must be >= 0");
    }

    Function transfer =
        new Function(
            "transfer",
            List.of(new Address(normalizedToAddress.value()), new Uint256(amountWei)),
            List.of());
    return FunctionEncoder.encode(transfer);
  }

  public static Web3ContractPort.SignedTransaction signTransfer(
      Web3ContractPort.SignTransferCommand command) {
    validate(command);

    String transferData = encodeTransferData(command.toAddress(), command.amountWei());
    RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            command.chainId(),
            BigInteger.valueOf(command.nonce()),
            command.gasLimit(),
            command.tokenContractAddress(),
            BigInteger.ZERO,
            transferData,
            command.maxPriorityFeePerGas(),
            command.maxFeePerGas());

    Credentials credentials = Credentials.create(command.treasuryPrivateKeyHex());
    byte[] signedBytes = TransactionEncoder.signMessage(rawTransaction, credentials);
    String rawTx = Numeric.toHexString(signedBytes);
    String txHash = Hash.sha3(rawTx);

    return new Web3ContractPort.SignedTransaction(rawTx, txHash);
  }

  private static void validate(Web3ContractPort.SignTransferCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    if (command.treasuryPrivateKeyHex() == null || command.treasuryPrivateKeyHex().isBlank()) {
      throw new Web3InvalidInputException("treasuryPrivateKeyHex is required");
    }
    EvmAddress.of(command.tokenContractAddress());
    EvmAddress.of(command.toAddress());
    if (command.nonce() < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (command.chainId() <= 0) {
      throw new Web3InvalidInputException("chainId must be > 0");
    }
    if (command.gasLimit() == null || command.gasLimit().signum() <= 0) {
      throw new Web3InvalidInputException("gasLimit must be > 0");
    }
    if (command.maxPriorityFeePerGas() == null || command.maxPriorityFeePerGas().signum() <= 0) {
      throw new Web3InvalidInputException("maxPriorityFeePerGas must be > 0");
    }
    if (command.maxFeePerGas() == null || command.maxFeePerGas().signum() <= 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be > 0");
    }
    if (command.maxFeePerGas().compareTo(command.maxPriorityFeePerGas()) < 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be >= maxPriorityFeePerGas");
    }
  }
}
