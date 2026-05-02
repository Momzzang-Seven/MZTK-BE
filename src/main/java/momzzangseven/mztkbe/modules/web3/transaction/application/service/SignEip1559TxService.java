package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.SignEip1559TxUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.SignedTx;
import org.springframework.stereotype.Service;

/**
 * Orchestrator for signing an EIP-1559 (Type-2) transaction via an externally-held key.
 *
 * <p>Combines the pure {@link Eip1559TxEncoder} (RLP build / digest / signed assemble) with a
 * {@link SignDigestPort} that hides the KMS round-trip behind a bridging adapter. Direct imports of
 * the {@code web3/shared} module are forbidden here per ARCHITECTURE.md — only the transaction-side
 * out-port is referenced.
 */
@Service
@RequiredArgsConstructor
public class SignEip1559TxService implements SignEip1559TxUseCase {

  private final SignDigestPort signDigestPort;

  /**
   * Build, digest, sign, and assemble an EIP-1559 transaction in one pass.
   *
   * @param fields validated EIP-1559 field set
   * @param kmsKeyId KMS key identifier used to sign the digest
   * @param signerAddress 0x-prefixed EVM address derived from the KMS key (used for recovery-id
   *     determination on the shared side)
   * @return signed transaction envelope ready for broadcast
   */
  @Override
  public SignedTx sign(Eip1559Fields fields, String kmsKeyId, String signerAddress) {
    byte[] unsigned = Eip1559TxEncoder.buildUnsigned(fields);
    byte[] digest = Eip1559TxEncoder.digest(unsigned);
    Vrs sig = signDigestPort.signDigest(kmsKeyId, digest, signerAddress);
    return Eip1559TxEncoder.assembleSigned(fields, sig);
  }
}
