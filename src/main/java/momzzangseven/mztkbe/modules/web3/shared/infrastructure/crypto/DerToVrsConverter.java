package momzzangseven.mztkbe.modules.web3.shared.infrastructure.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Locale;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Pure utility that converts an AWS KMS DER-encoded ECDSA signature into the canonical Ethereum
 * {@link Vrs} {@code (r, s, v)} form.
 *
 * <p>The conversion pipeline is:
 *
 * <ol>
 *   <li><b>DER decode</b> — parse the ASN.1 {@code SEQUENCE { INTEGER r, INTEGER s }} produced by
 *       AWS KMS {@code Sign(MessageType=DIGEST, ECDSA_SHA_256)}.
 *   <li><b>Low-s correction (EIP-2)</b> — secp256k1 has two valid {@code s} values for any
 *       signature ({@code s} and {@code n - s}); Ethereum mandates the low half ({@code s ≤ n/2}).
 *       This is applied unconditionally, regardless of whether the source already produced low-s.
 *   <li><b>Recovery id determination</b> — for {@code v ∈ {27, 28}}, recover the public key via
 *       {@link Sign#recoverFromSignature(int, ECDSASignature, byte[])}, derive its address via
 *       {@link Keys#getAddress(BigInteger)}, and return the first {@code v} whose recovered address
 *       matches {@code expectedAddress} (case-insensitive, no checksum validation).
 * </ol>
 *
 * <p>If neither candidate {@code v} recovers an address matching {@code expectedAddress}, a {@link
 * SignatureRecoveryException} is thrown.
 *
 * <p>This class is stateless and has no Spring or logging dependencies — it lives in the domain
 * layer and is invoked by infrastructure adapters (e.g. {@code KmsSignerAdapter}) that talk to AWS
 * KMS or to local fallback signers.
 */
public final class DerToVrsConverter {

  /** secp256k1 curve order {@code n}. Sourced from web3j's bundled curve parameters. */
  private static final BigInteger CURVE_N = Sign.CURVE_PARAMS.getN();

  /** {@code n / 2}, used as the low-s upper bound per EIP-2. */
  private static final BigInteger CURVE_HALF_N = CURVE_N.shiftRight(1);

  /** Width of an Ethereum signature scalar component in bytes. */
  private static final int SCALAR_BYTE_LENGTH = 32;

  /** Required length of an Ethereum keccak-256 digest in bytes. */
  private static final int DIGEST_BYTE_LENGTH = 32;

  /** Lower candidate value of the Ethereum {@code v} recovery identifier. */
  private static final byte V_BASE = 27;

  /** Utility class — must not be instantiated. */
  private DerToVrsConverter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Convert a DER-encoded ECDSA signature into Ethereum {@code (r, s, v)} form.
   *
   * @param derSignature ASN.1 DER bytes returned by AWS KMS {@code Sign}
   * @param digest the 32-byte digest that was signed (usually a Keccak-256 hash)
   * @param expectedAddress the {@code 0x}-prefixed 40-hex Ethereum address bound to the signing KMS
   *     key; comparison is performed lowercase-insensitively (no checksum validation)
   * @return canonical {@link Vrs} with 32-byte {@code r} / {@code s} and {@code v ∈ {27, 28}}
   * @throws SignatureRecoveryException if the DER bytes cannot be parsed or if neither {@code v=27}
   *     nor {@code v=28} recovers an address matching {@code expectedAddress}
   */
  public static Vrs convert(byte[] derSignature, byte[] digest, String expectedAddress) {
    if (digest == null || digest.length != DIGEST_BYTE_LENGTH) {
      throw new SignatureRecoveryException(
          "digest must be a non-null " + DIGEST_BYTE_LENGTH + "-byte array");
    }
    final ECDSASignature signature = decodeAndNormalise(derSignature);
    final String expectedNormalised = normaliseAddress(expectedAddress);

    for (byte v = V_BASE; v <= V_BASE + 1; v++) {
      final BigInteger publicKey = Sign.recoverFromSignature(v - V_BASE, signature, digest);
      if (publicKey == null) {
        continue;
      }
      final String recoveredAddress = Keys.getAddress(publicKey);
      if (recoveredAddress.equalsIgnoreCase(expectedNormalised)) {
        return new Vrs(
            Numeric.toBytesPadded(signature.r, SCALAR_BYTE_LENGTH),
            Numeric.toBytesPadded(signature.s, SCALAR_BYTE_LENGTH),
            v);
      }
    }

    throw new SignatureRecoveryException(
        "Failed to recover Ethereum address matching expected wallet from KMS DER signature");
  }

  /**
   * Decode a DER ASN.1 {@code SEQUENCE { INTEGER r, INTEGER s }} and apply EIP-2 low-s correction.
   *
   * @param derSignature DER-encoded signature bytes
   * @return canonical (low-s) {@link ECDSASignature}
   * @throws SignatureRecoveryException if {@code derSignature} is malformed
   */
  private static ECDSASignature decodeAndNormalise(byte[] derSignature) {
    if (derSignature == null) {
      throw new SignatureRecoveryException("Malformed DER ECDSA signature");
    }
    try (ASN1InputStream asn1Stream = new ASN1InputStream(derSignature)) {
      final ASN1Sequence sequence = (ASN1Sequence) asn1Stream.readObject();
      if (sequence == null || sequence.size() != 2) {
        throw new SignatureRecoveryException(
            "DER signature is not a 2-element ASN.1 sequence (r, s)");
      }
      final BigInteger r = ((ASN1Integer) sequence.getObjectAt(0)).getValue();
      BigInteger s = ((ASN1Integer) sequence.getObjectAt(1)).getValue();
      if (s.compareTo(CURVE_HALF_N) > 0) {
        s = CURVE_N.subtract(s);
      }
      return new ECDSASignature(r, s);
    } catch (IOException | ClassCastException | IllegalArgumentException ex) {
      throw new SignatureRecoveryException("Malformed DER ECDSA signature", ex);
    }
  }

  /**
   * Strip the {@code 0x} prefix (if present) and lowercase the address for comparison.
   *
   * @param address the {@code 0x}-prefixed or unprefixed 40-hex Ethereum address
   * @return the address in lowercase form, with no {@code 0x} prefix
   */
  private static String normaliseAddress(String address) {
    if (address == null) {
      throw new SignatureRecoveryException("expectedAddress must not be null");
    }
    final String lower = address.toLowerCase(Locale.ROOT);
    return lower.startsWith("0x") ? lower.substring(2) : lower;
  }
}
