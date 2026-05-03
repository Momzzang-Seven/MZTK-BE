package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.web3;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase;
import org.springframework.stereotype.Component;

/**
 * Adapter that bridges {@link ExecutionEip1559SigningPort.SignCommand} into the shared {@link
 * SignEip1559TxUseCase} pipeline.
 *
 * <p>No plaintext key material is referenced; the signing capability is fully expressed by the
 * {@link TreasurySigner} embedded in the {@link ExecutionEip1559SigningPort.SignCommand}. Per
 * ARCHITECTURE.md, infrastructure depends on application via port/in or port/out — never on a
 * concrete service class, and never on another module's infrastructure.
 */
@Component
@RequiredArgsConstructor
public class ExecutionEip1559SigningAdapter implements ExecutionEip1559SigningPort {

  private final SignEip1559TxUseCase signEip1559TxUseCase;

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

    SignEip1559TxResult result =
        signEip1559TxUseCase.sign(
            new SignEip1559TxCommand(fields, signer.kmsKeyId(), signer.walletAddress()));
    SignedTx signed = result.signedTx();
    return new SignedTransaction(signed.rawTx(), signed.txHash());
  }

  private BigInteger nullSafe(BigInteger value) {
    return value == null ? BigInteger.ZERO : value;
  }
}
