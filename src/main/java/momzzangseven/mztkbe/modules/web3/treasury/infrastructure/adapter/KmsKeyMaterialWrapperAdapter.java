package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import org.springframework.stereotype.Component;

/**
 * Pure-JCE implementation of {@link KmsKeyMaterialWrapperPort}.
 *
 * <p>Wraps the raw 32-byte secp256k1 private key with the RSA-4096 wrapping public key returned by
 * {@code KmsKeyLifecyclePort.getParametersForImport()} using {@code RSA/ECB/OAEP} with SHA-256
 * digest and MGF1-SHA-256, matching AWS KMS {@code WrappingAlgorithm=RSAES_OAEP_SHA_256}. The
 * wrapping happens entirely in-process so the raw private key never appears on the wire — only the
 * ciphertext is uploaded via {@code ImportKeyMaterial}.
 */
@Component
public class KmsKeyMaterialWrapperAdapter implements KmsKeyMaterialWrapperPort {

  private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPPadding";
  private static final String KEY_FACTORY_ALGORITHM = "RSA";

  @Override
  public byte[] wrap(byte[] rawKey, byte[] wrappingPublicKey) {
    if (rawKey == null || rawKey.length == 0) {
      throw new Web3InvalidInputException("rawKey must not be empty");
    }
    if (wrappingPublicKey == null || wrappingPublicKey.length == 0) {
      throw new Web3InvalidInputException("wrappingPublicKey must not be empty");
    }
    try {
      KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
      PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(wrappingPublicKey));

      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
      OAEPParameterSpec oaepParams =
          new OAEPParameterSpec(
              "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
      cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);
      return cipher.doFinal(rawKey);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("RSA-OAEP-SHA-256 wrap failed", e);
    }
  }
}
