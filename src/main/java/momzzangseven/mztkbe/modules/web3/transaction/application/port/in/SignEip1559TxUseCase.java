package momzzangseven.mztkbe.modules.web3.transaction.application.port.in;

import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.SignedTx;

/**
 * Build, digest, sign, and assemble an EIP-1559 (Type-2) transaction via an externally-held key.
 *
 * <p>Driving adapters (e.g. {@code Eip1559TxSigningAdapter}) depend on this interface rather than
 * the concrete service, per ARCHITECTURE.md's {@code infrastructure → application(port/out only) +
 * domain} dependency rule.
 */
public interface SignEip1559TxUseCase {

  SignedTx sign(Eip1559Fields fields, String kmsKeyId, String signerAddress);
}
