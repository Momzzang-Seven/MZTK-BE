package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.springframework.stereotype.Component;

/**
 * Pure-JCE implementation of {@link KmsKeyMaterialWrapperPort}.
 *
 * <p>Wraps a 32-byte secp256k1 raw scalar with the RSA-4096 wrapping public key returned by {@code
 * KmsKeyLifecyclePort.getParametersForImport()} using {@code RSA/ECB/OAEP} with SHA-256 digest and
 * MGF1-SHA-256 (matching AWS KMS {@code WrappingAlgorithm=RSAES_OAEP_SHA_256}).
 *
 * <p>AWS KMS {@code ImportKeyMaterial} for asymmetric ECC keys does not accept the raw scalar — the
 * key material must be a PKCS#8 {@code PrivateKeyInfo} DER whose inner {@code ECPrivateKey} encodes
 * the scalar. This adapter builds the PKCS#8 envelope before wrapping so the prod KMS call
 * succeeds.
 */
@Component
public class KmsKeyMaterialWrapperAdapter implements KmsKeyMaterialWrapperPort {

  private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPPadding";
  private static final String KEY_FACTORY_ALGORITHM = "RSA";
  private static final int SECP256K1_ORDER_BITS = 256;
  private static final int RAW_SCALAR_BYTES = 32;

  @Override
  public byte[] wrap(byte[] rawKey, byte[] wrappingPublicKey) {
    if (rawKey == null || rawKey.length == 0) {
      throw new Web3InvalidInputException("rawKey must not be empty");
    }
    if (wrappingPublicKey == null || wrappingPublicKey.length == 0) {
      throw new Web3InvalidInputException("wrappingPublicKey must not be empty");
    }
    byte[] pkcs8 = encodeSecp256k1Pkcs8(rawKey);
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
      PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(wrappingPublicKey));

      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      OAEPParameterSpec oaepParams =
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
      cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);
      return cipher.doFinal(pkcs8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("RSA-OAEP-SHA-256 wrap failed", e);
    } finally {
      Arrays.fill(pkcs8, (byte) 0);
    }
  }

  /**
   * Build a PKCS#8 {@code PrivateKeyInfo} DER for a secp256k1 EC private key whose scalar is the
   * supplied raw bytes. The curve OID lives in the outer {@code AlgorithmIdentifier} (named-curve
   * form) so the inner {@code ECPrivateKey} carries only the version and the scalar.
   */
  static byte[] encodeSecp256k1Pkcs8(byte[] rawScalar) {
    if (rawScalar.length != RAW_SCALAR_BYTES) {
      throw new Web3InvalidInputException(
          "rawKey must be a " + RAW_SCALAR_BYTES + "-byte secp256k1 scalar");
    }
    BigInteger d = new BigInteger(1, rawScalar);
    try {
      ECPrivateKey ecPrivateKey = new ECPrivateKey(SECP256K1_ORDER_BITS, d);
      AlgorithmIdentifier algId =
          new AlgorithmIdentifier(
              X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1);
      return new PrivateKeyInfo(algId, ecPrivateKey).getEncoded();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to DER-encode secp256k1 PKCS#8 PrivateKeyInfo", e);
    }
  }
}
