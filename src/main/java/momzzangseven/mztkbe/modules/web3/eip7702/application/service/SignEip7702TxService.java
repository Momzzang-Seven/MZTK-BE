package momzzangseven.mztkbe.modules.web3.eip7702.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.Eip7702Fields;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;

/**
 * Orchestrates the EIP-7702 build → digest → sign → assemble pipeline via {@link SignDigestPort}.
 */
// @Service is deferred to commit 3-3 along with the matching SignDigestAdapter; registering this
// bean now would fail context startup because no SignDigestPort implementation exists yet.
@RequiredArgsConstructor
public final class SignEip7702TxService {

  private final SignDigestPort signDigestPort;

  /**
   * Build, digest, sign, and assemble an EIP-7702 (Type-4) transaction in one pass.
   *
   * @param fields validated EIP-7702 field set
   * @param signer KMS-backed treasury signer capability handle (no secret material)
   * @return signed transaction envelope ready for broadcast
   */
  public SignedTx sign(Eip7702Fields fields, TreasurySigner signer) {

    // build RLP encoded(serialized) byte
    byte[] unsigned = Eip7702TxEncoder.buildUnsigned(fields);

    // Hashing the encoded(serialized) byte
    byte[] digest = Eip7702TxEncoder.digest(unsigned);

    // Make signature from KMS
    Vrs vrs = signDigestPort.signDigest(signer.kmsKeyId(), digest, signer.walletAddress());

    return Eip7702TxEncoder.assembleSigned(fields, vrs);
  }
}
