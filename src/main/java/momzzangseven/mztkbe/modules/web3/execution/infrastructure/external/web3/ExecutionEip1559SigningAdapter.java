package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.web3;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.shared.domain.encoder.Eip1559TxEncoder;
import momzzangseven.mztkbe.modules.web3.shared.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.domain.encoder.Eip1559TxEncoder.SignedTx;
import org.springframework.stereotype.Component;

/**
 * Adapter that bridges {@link ExecutionEip1559SigningPort.SignCommand} into the pure {@link
 * Eip1559TxEncoder} pipeline plus a KMS-backed digest signature obtained through the
 * execution-local {@link SignDigestPort}.
 *
 * <p>No plaintext key material is referenced; the signing capability is fully expressed by the
 * {@link TreasurySigner} embedded in the {@link ExecutionEip1559SigningPort.SignCommand}. The
 * adapter never imports the shared {@code SignDigestUseCase} directly per ARCHITECTURE.md.
 */
@Component
@RequiredArgsConstructor
public class ExecutionEip1559SigningAdapter implements ExecutionEip1559SigningPort {

  private final SignDigestPort signDigestPort;

  @Override
  public SignedTransaction sign(SignCommand command) {
    if (command == null) {
      throw new Web3InvalidInputException("command is required");
    }
    TreasurySigner signer = command.signer();

    Eip1559Fields fields =
        new Eip1559Fields(
            command.chainId(),
            command.nonce(),
            command.maxPriorityFeePerGas(),
            command.maxFeePerGas(),
            command.gasLimit(),
            command.toAddress(),
            nullSafe(command.valueWei()),
            command.data());

    byte[] unsigned = Eip1559TxEncoder.buildUnsigned(fields);
    byte[] digest = Eip1559TxEncoder.digest(unsigned);
    Vrs vrs = signDigestPort.signDigest(signer.kmsKeyId(), digest, signer.walletAddress());
    SignedTx signed = Eip1559TxEncoder.assembleSigned(fields, vrs);
    return new SignedTransaction(signed.rawTx(), signed.txHash());
  }

  private BigInteger nullSafe(BigInteger value) {
    return value == null ? BigInteger.ZERO : value;
  }
}
