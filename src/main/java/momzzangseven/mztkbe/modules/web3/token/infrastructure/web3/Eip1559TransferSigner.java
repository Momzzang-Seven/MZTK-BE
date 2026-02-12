package momzzangseven.mztkbe.modules.web3.token.infrastructure.web3;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.Web3ContractPort;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;

/** Stateless signer for ERC-20 transfer EIP-1559 transaction payloads. */
public final class Eip1559TransferSigner {

  private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(120_000L);
  private static final BigInteger DEFAULT_MAX_PRIORITY_FEE_PER_GAS =
      BigInteger.valueOf(1_000_000_000L);

  private Eip1559TransferSigner() {}

  public static String encodeTransferData(String toAddress, BigInteger amountWei) {
    if (toAddress == null || !WalletUtils.isValidAddress(toAddress)) {
      throw new Web3InvalidInputException("toAddress is invalid");
    }
    if (amountWei == null || amountWei.signum() < 0) {
      throw new Web3InvalidInputException("amountWei must be >= 0");
    }

    Function transfer =
        new Function(
            "transfer", List.of(new Address(toAddress), new Uint256(amountWei)), List.of());
    return FunctionEncoder.encode(transfer);
  }

  public static Web3ContractPort.SignedTransaction signTransfer(
      Web3ContractPort.SignTransferCommand command) {
    validate(command);

    BigInteger gasLimit = positiveOrDefault(command.gasLimit(), DEFAULT_GAS_LIMIT);
    BigInteger maxPriorityFeePerGas =
        positiveOrDefault(command.maxPriorityFeePerGas(), DEFAULT_MAX_PRIORITY_FEE_PER_GAS);
    BigInteger maxFeePerGas =
        positiveOrDefault(command.maxFeePerGas(), maxPriorityFeePerGas.multiply(BigInteger.TWO));

    String transferData = encodeTransferData(command.toAddress(), command.amountWei());
    RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            command.chainId(),
            BigInteger.valueOf(command.nonce()),
            gasLimit,
            command.tokenContractAddress(),
            BigInteger.ZERO,
            transferData,
            maxPriorityFeePerGas,
            maxFeePerGas);

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
    if (command.tokenContractAddress() == null
        || !WalletUtils.isValidAddress(command.tokenContractAddress())) {
      throw new Web3InvalidInputException("tokenContractAddress is invalid");
    }
    if (command.toAddress() == null || !WalletUtils.isValidAddress(command.toAddress())) {
      throw new Web3InvalidInputException("toAddress is invalid");
    }
    if (command.nonce() < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (command.chainId() <= 0) {
      throw new Web3InvalidInputException("chainId must be > 0");
    }
  }

  private static BigInteger positiveOrDefault(BigInteger value, BigInteger defaultValue) {
    if (value == null || value.signum() <= 0) {
      return defaultValue;
    }
    return value;
  }
}
