package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.web3;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

@Component
public class ExecutionEip1559SigningAdapter implements ExecutionEip1559SigningPort {

  @Override
  public SignedTransaction sign(SignCommand command) {
    validate(command);

    RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            command.chainId(),
            BigInteger.valueOf(command.nonce()),
            command.gasLimit(),
            command.toAddress(),
            nullSafe(command.valueWei()),
            normalizeData(command.data()),
            command.maxPriorityFeePerGas(),
            command.maxFeePerGas());

    Credentials credentials = Credentials.create(command.signerPrivateKeyHex());
    byte[] signedBytes = TransactionEncoder.signMessage(rawTransaction, credentials);
    String rawTx = Numeric.toHexString(signedBytes);
    return new SignedTransaction(rawTx, Hash.sha3(rawTx));
  }

  private void validate(SignCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    if (command.chainId() <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (command.nonce() < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    EvmAddress.of(command.toAddress());
    if (command.gasLimit() == null || command.gasLimit().signum() <= 0) {
      throw new Web3InvalidInputException("gasLimit must be positive");
    }
    if (command.maxPriorityFeePerGas() == null || command.maxPriorityFeePerGas().signum() <= 0) {
      throw new Web3InvalidInputException("maxPriorityFeePerGas must be positive");
    }
    if (command.maxFeePerGas() == null || command.maxFeePerGas().signum() <= 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be positive");
    }
    if (command.maxFeePerGas().compareTo(command.maxPriorityFeePerGas()) < 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be >= maxPriorityFeePerGas");
    }
    if (command.signerPrivateKeyHex() == null || command.signerPrivateKeyHex().isBlank()) {
      throw new Web3InvalidInputException("signerPrivateKeyHex is required");
    }
  }

  private String normalizeData(String data) {
    if (data == null || data.isBlank()) {
      return "0x";
    }
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(data));
  }

  private BigInteger nullSafe(BigInteger value) {
    return value == null ? BigInteger.ZERO : value;
  }
}
