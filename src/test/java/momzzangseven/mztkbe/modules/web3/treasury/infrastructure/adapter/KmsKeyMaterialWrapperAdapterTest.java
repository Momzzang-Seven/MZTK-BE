package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

class KmsKeyMaterialWrapperAdapterTest {

  private final KmsKeyMaterialWrapperAdapter adapter = new KmsKeyMaterialWrapperAdapter();

  @Test
  void encodeSecp256k1Pkcs8_emitsValidPrivateKeyInfoWithSecp256k1Oid() throws Exception {
    BigInteger d =
        Numeric.toBigInt("0x1111111111111111111111111111111111111111111111111111111111111111");
    byte[] scalar = Numeric.toBytesPadded(d, 32);

    byte[] pkcs8 = KmsKeyMaterialWrapperAdapter.encodeSecp256k1Pkcs8(scalar);

    PrivateKeyInfo decoded = PrivateKeyInfo.getInstance(pkcs8);
    AlgorithmIdentifier algId = decoded.getPrivateKeyAlgorithm();
    assertThat(algId.getAlgorithm()).isEqualTo(X9ObjectIdentifiers.id_ecPublicKey);
    assertThat(algId.getParameters()).isEqualTo(SECObjectIdentifiers.secp256k1);

    ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(decoded.parsePrivateKey());
    assertThat(ecPrivateKey.getKey()).isEqualTo(d);
  }

  @Test
  void encodeSecp256k1Pkcs8_padsLeadingZerosFromShortScalar() {
    byte[] shortBytes = new byte[32];
    shortBytes[31] = 0x01;

    byte[] pkcs8 = KmsKeyMaterialWrapperAdapter.encodeSecp256k1Pkcs8(shortBytes);
    PrivateKeyInfo decoded = PrivateKeyInfo.getInstance(pkcs8);
    ECPrivateKey ecPrivateKey;
    try {
      ecPrivateKey = ECPrivateKey.getInstance(decoded.parsePrivateKey());
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    assertThat(ecPrivateKey.getKey()).isEqualTo(BigInteger.ONE);
  }

  @Test
  void encodeSecp256k1Pkcs8_rejectsWrongLength() {
    assertThatThrownBy(() -> KmsKeyMaterialWrapperAdapter.encodeSecp256k1Pkcs8(new byte[31]))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  void wrap_isInvertibleWithMatchingRsaPrivateKey() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair rsa = gen.generateKeyPair();

    BigInteger d =
        Numeric.toBigInt("0x2222222222222222222222222222222222222222222222222222222222222222");
    byte[] scalar = Numeric.toBytesPadded(d, 32);

    byte[] wrapped = adapter.wrap(scalar, rsa.getPublic().getEncoded());
    byte[] unwrapped = oaepDecrypt(rsa.getPrivate(), wrapped);

    PrivateKeyInfo decoded = PrivateKeyInfo.getInstance(unwrapped);
    ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(decoded.parsePrivateKey());
    assertThat(ecPrivateKey.getKey()).isEqualTo(d);
  }

  @Test
  void wrap_rejectsEmptyInputs() {
    assertThatThrownBy(() -> adapter.wrap(new byte[0], new byte[] {1}))
        .isInstanceOf(Web3InvalidInputException.class);
    assertThatThrownBy(() -> adapter.wrap(new byte[32], new byte[0]))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  void wrap_innerEcPrivateKeyDoesNotEmbedCurveParameters() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair rsa = gen.generateKeyPair();

    byte[] scalar = new byte[32];
    scalar[31] = 0x42;

    byte[] wrapped = adapter.wrap(scalar, rsa.getPublic().getEncoded());
    byte[] unwrapped = oaepDecrypt(rsa.getPrivate(), wrapped);

    PrivateKeyInfo decoded = PrivateKeyInfo.getInstance(unwrapped);
    ASN1Sequence inner = ASN1Sequence.getInstance(decoded.parsePrivateKey().toASN1Primitive());
    ASN1Encodable version = inner.getObjectAt(0);
    assertThat(((ASN1Integer) version).getValue()).isEqualTo(BigInteger.ONE);
    // Either no parameters/publicKey context-tagged elements at all, or only context tags > 0:
    // size 2 = (version, privateKey) means parameters live in AlgorithmIdentifier (PKCS#8 form).
    assertThat(inner.size()).isEqualTo(2);
  }

  private static byte[] oaepDecrypt(PrivateKey rsaPrivate, byte[] wrapped) throws Exception {
    Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
    OAEPParameterSpec spec =
        new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    cipher.init(Cipher.DECRYPT_MODE, rsaPrivate, spec);
    return cipher.doFinal(wrapped);
  }
}
