package momzzangseven.mztkbe.modules.web3.eip7702.application.port.out;

import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.Eip7702Fields;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;

/**
 * Application-level out-port that exposes the EIP-7702 build → digest → sign → assemble pipeline to
 * infrastructure adapters. Decouples {@code Eip7702TransactionCodecAdapter} (infra) from the
 * concrete {@code SignEip7702TxService} class so the layering rule "infrastructure depends on
 * ports, not application services" stays intact.
 */
public interface SignEip7702TxPort {

  /**
   * Build, digest, sign, and assemble a Type-4 (EIP-7702) transaction envelope for the supplied
   * field set.
   *
   * @param fields validated EIP-7702 field set
   * @param signer KMS-backed treasury signer capability handle (no secret material)
   * @return signed transaction envelope ready for broadcast
   */
  SignedTx sign(Eip7702Fields fields, TreasurySigner signer);
}
