package momzzangseven.mztkbe.modules.web3.shared.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.Eip1559TxCodecPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.springframework.stereotype.Service;

/**
 * Cross-module orchestrator for signing an EIP-1559 transaction via AWS KMS.
 *
 * <p>Combines the codec out-port (web3j-based RLP build / digest / signed assemble) with the
 * existing shared {@link SignDigestUseCase} so the entire build → digest → KMS sign → assemble
 * pipeline lives at one entry point. Sibling web3 modules call this through {@link
 * SignEip1559TxUseCase}.
 */
@Service
@RequiredArgsConstructor
public class SignEip1559TxService implements SignEip1559TxUseCase {

  private final Eip1559TxCodecPort codec;
  private final SignDigestUseCase signDigestUseCase;

  @Override
  public SignEip1559TxResult sign(SignEip1559TxCommand command) {
    byte[] unsigned = codec.buildUnsigned(command.fields());
    byte[] digest = codec.digest(unsigned);
    SignDigestResult digestResult =
        signDigestUseCase.execute(
            new SignDigestCommand(command.kmsKeyId(), digest, command.expectedSignerAddress()));
    Vrs vrs = new Vrs(digestResult.r(), digestResult.s(), digestResult.v());
    SignedTx signedTx = codec.assembleSigned(command.fields(), vrs);
    return new SignEip1559TxResult(signedTx);
  }
}
