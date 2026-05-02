package momzzangseven.mztkbe.modules.web3.shared.application.port.out;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/**
 * Out-port for EIP-1559 (Type-2) transaction codec — build / digest / assemble.
 *
 * <p>Implementation isolates web3j (RLP, keccak, hex) inside {@code
 * web3/shared/infrastructure/adapter/}, keeping the application-layer signing service framework-
 * free at the type level. Cross-module callers must not depend on this port — they go through
 * {@link
 * momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase} instead.
 */
public interface Eip1559TxCodecPort {

  /** Unsigned typed envelope bytes: {@code 0x02 ‖ rlp([...fields, accessList=[]])}. */
  byte[] buildUnsigned(Eip1559Fields fields);

  /** keccak256 of unsigned bytes — the digest a remote signer (KMS) consumes. */
  byte[] digest(byte[] unsigned);

  /** Signed envelope: {@code 0x02 ‖ rlp([..., yParity, r, s])}, packaged with txHash. */
  SignedTx assembleSigned(Eip1559Fields fields, Vrs sig);
}
