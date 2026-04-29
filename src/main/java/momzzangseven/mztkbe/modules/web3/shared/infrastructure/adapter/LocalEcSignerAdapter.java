package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.KmsSignerPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.crypto.DerToVrsConverter;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

/**
 * Non-production implementation of {@link KmsSignerPort} that performs secp256k1 signing locally
 * with BouncyCastle / web3j instead of calling AWS KMS.
 *
 * <p>This adapter is gated on the absence of {@code web3.kms.enabled=true} (the inverse of {@link
 * KmsSignerAdapter}) and is the bean wired in for environments that have not opted into real AWS
 * KMS — typically local / dev / test / integration / E2E. In environments that opt in (prod, or any
 * developer who has KMS access and sets {@code web3.kms.enabled=true}) the equivalent role is
 * filled by {@link KmsSignerAdapter}, which delegates the same digest signing to a real AWS KMS
 * HSM.
 *
 * <p><b>Plaintext private keys never leave this class.</b> The in-memory {@code kmsKeyId →
 * BigInteger} map is populated <em>exclusively</em> by tests via {@link #registerKey(String,
 * BigInteger)} and emptied by {@link #clear()}. Production-style code paths cannot reach this class
 * when {@code web3.kms.enabled=true} because the {@code @ConditionalOnProperty} gate excludes it at
 * DI time. {@code SignDigestService} receives the same {@link Vrs} contract regardless of which
 * adapter signs, so the converter + recovery-id logic in {@link DerToVrsConverter} is exercised
 * end-to-end in both modes.
 *
 * <p>Why route through {@link DerToVrsConverter} rather than returning {@code Sign.signMessage}'s
 * {@code (r, s, v)} directly? To guarantee that the EIP-2 low-s + recovery-id determination path is
 * the <em>same code</em> in non-prod as in prod. We re-encode the freshly produced {@code (r, s)}
 * as a DER signature so the converter performs the identical decode → low-s → ecRecover pipeline.
 */
@Component
@ConditionalOnProperty(name = "web3.kms.enabled", havingValue = "false", matchIfMissing = true)
public class LocalEcSignerAdapter implements KmsSignerPort {

  /**
   * Test-only registry mapping a logical {@code kmsKeyId} (the same string production calls would
   * pass through {@link KmsSignerPort}) to the raw secp256k1 private key. Empty in production-like
   * Spring contexts; populated only by test setup.
   */
  private final Map<String, BigInteger> kmsKeyIdToPrivateKey = new ConcurrentHashMap<>();

  /**
   * Register a logical {@code kmsKeyId} → raw private key mapping. Tests call this in
   * {@code @BeforeAll} (or equivalent) to bind a fixture key to the same alias used in production
   * pathways. Never called from production-style code, since this bean is only present when {@code
   * web3.kms.enabled} is false / unset.
   *
   * @param kmsKeyId the logical key identifier passed by test fixtures (any non-blank string)
   * @param privateKey raw secp256k1 private key as a positive {@link BigInteger}
   */
  public void registerKey(String kmsKeyId, BigInteger privateKey) {
    kmsKeyIdToPrivateKey.put(kmsKeyId, privateKey);
  }

  /**
   * Clear all registered keys. Tests call this in {@code @AfterAll} (or equivalent) to ensure the
   * in-memory key map does not leak across test classes / Spring contexts.
   */
  public void clear() {
    kmsKeyIdToPrivateKey.clear();
  }

  @Override
  public Vrs signDigest(String kmsKeyId, byte[] digest, String expectedAddress) {
    final BigInteger privateKey = kmsKeyIdToPrivateKey.get(kmsKeyId);
    if (privateKey == null) {
      throw new KmsSignFailedException(
          "Local signer has no key registered for kmsKeyId=" + kmsKeyId);
    }
    final ECKeyPair keyPair = ECKeyPair.create(privateKey);
    final Sign.SignatureData rawSignature = Sign.signMessage(digest, keyPair, false);

    final byte[] derSignature;
    try {
      derSignature =
          encodeDer(new BigInteger(1, rawSignature.getR()), new BigInteger(1, rawSignature.getS()));
    } catch (IOException ex) {
      throw new KmsSignFailedException("Failed to DER-encode local secp256k1 signature", ex);
    }

    return DerToVrsConverter.convert(derSignature, digest, expectedAddress);
  }

  /**
   * Encode {@code (r, s)} as an ASN.1 DER {@code SEQUENCE { INTEGER r, INTEGER s }} matching the
   * shape returned by AWS KMS {@code Sign}. Routing through DER allows {@link DerToVrsConverter} to
   * apply the identical low-s + recovery-id logic in both prod and non-prod.
   *
   * @param r the secp256k1 {@code r} component
   * @param s the secp256k1 {@code s} component
   * @return DER-encoded signature bytes
   * @throws IOException if BouncyCastle ASN.1 encoding fails (should not occur in practice)
   */
  private static byte[] encodeDer(BigInteger r, BigInteger s) throws IOException {
    final ASN1EncodableVector vec = new ASN1EncodableVector();
    vec.add(new ASN1Integer(r));
    vec.add(new ASN1Integer(s));
    return new DERSequence(vec).getEncoded();
  }
}
