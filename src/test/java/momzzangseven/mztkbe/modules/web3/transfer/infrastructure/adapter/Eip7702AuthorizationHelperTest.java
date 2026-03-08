package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

class Eip7702AuthorizationHelperTest {

  @Test
  void buildSigningHash_returnsDeterministic32Bytes() {
    byte[] first =
        Eip7702AuthorizationHelper.buildSigningHash(
            11155111L, "0x" + "a".repeat(40), BigInteger.ZERO);
    byte[] second =
        Eip7702AuthorizationHelper.buildSigningHash(
            11155111L, "0x" + "a".repeat(40), BigInteger.ZERO);

    assertThat(first).hasSize(32);
    assertThat(second).isEqualTo(first);
  }

  @Test
  void buildSigningHash_rejectsInvalidInputs() {
    assertThatThrownBy(
            () ->
                Eip7702AuthorizationHelper.buildSigningHash(
                    0L, "0x" + "a".repeat(40), BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("chainId");

    assertThatThrownBy(() -> Eip7702AuthorizationHelper.buildSigningHash(1L, " ", BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("delegateTarget");

    assertThatThrownBy(() -> Eip7702AuthorizationHelper.buildSigningHash(1L, null, BigInteger.ZERO))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("delegateTarget");

    assertThatThrownBy(
            () -> Eip7702AuthorizationHelper.buildSigningHash(1L, "0x" + "a".repeat(40), null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");

    assertThatThrownBy(
            () ->
                Eip7702AuthorizationHelper.buildSigningHash(
                    1L, "0x" + "a".repeat(40), BigInteger.valueOf(-1)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");
  }

  @Test
  void verifySigner_andRecoverAddress_workForValidSignature() {
    ECKeyPair keyPair = ECKeyPair.create(BigInteger.TEN);
    byte[] hash =
        Eip7702AuthorizationHelper.buildSigningHash(
            11155111L, "0x" + "a".repeat(40), BigInteger.ONE);
    String signatureHex = signatureHex(Sign.signMessage(hash, keyPair, false));
    String expectedAddress = "0x" + Keys.getAddress(keyPair.getPublicKey());

    assertThat(
            Eip7702AuthorizationHelper.verifySigner(
                11155111L, "0x" + "a".repeat(40), BigInteger.ONE, signatureHex, expectedAddress))
        .isTrue();
    assertThat(
            Eip7702AuthorizationHelper.verifySigner(
                11155111L,
                "0x" + "a".repeat(40),
                BigInteger.ONE,
                signatureHex,
                "0x" + "b".repeat(40)))
        .isFalse();
    assertThat(Eip7702AuthorizationHelper.recoverAddress(hash, signatureHex))
        .isEqualTo(expectedAddress.toLowerCase());
  }

  @Test
  void toAuthorizationTuple_parsesSignatureAndNormalizesYParity() {
    ECKeyPair keyPair = ECKeyPair.create(BigInteger.valueOf(11));
    byte[] hash =
        Eip7702AuthorizationHelper.buildSigningHash(
            11155111L, "0x" + "a".repeat(40), BigInteger.valueOf(2));
    String signatureHex = signatureHex(Sign.signMessage(hash, keyPair, false));

    Eip7702ChainPort.AuthorizationTuple tuple =
        Eip7702AuthorizationHelper.toAuthorizationTuple(
            11155111L, "0x" + "a".repeat(40), BigInteger.valueOf(2), signatureHex);

    assertThat(tuple.chainId()).isEqualTo(BigInteger.valueOf(11155111L));
    assertThat(tuple.nonce()).isEqualTo(BigInteger.valueOf(2));
    assertThat(tuple.yParity()).isBetween(BigInteger.ZERO, BigInteger.ONE);
  }

  @Test
  void toAuthorizationTuple_supportsLegacyVFormat() {
    byte[] signature = new byte[65];
    signature[64] = 1;
    String signatureHex = Numeric.toHexString(signature);

    Eip7702ChainPort.AuthorizationTuple tuple =
        Eip7702AuthorizationHelper.toAuthorizationTuple(
            11155111L, "0x" + "a".repeat(40), BigInteger.ZERO, signatureHex);

    assertThat(tuple.yParity()).isEqualTo(BigInteger.ONE);
  }

  @Test
  void recoverAddress_rejectsInvalidSignatureEncoding() {
    byte[] hash =
        Eip7702AuthorizationHelper.buildSigningHash(
            11155111L, "0x" + "a".repeat(40), BigInteger.ONE);

    assertThatThrownBy(() -> Eip7702AuthorizationHelper.recoverAddress(hash, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signature is required");

    assertThatThrownBy(() -> Eip7702AuthorizationHelper.recoverAddress(hash, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signature is required");

    assertThatThrownBy(() -> Eip7702AuthorizationHelper.recoverAddress(hash, "0x1234"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signature must be 65 bytes");

    byte[] signature = new byte[65];
    signature[64] = 31; // recId=4 -> invalid
    assertThatThrownBy(
            () -> Eip7702AuthorizationHelper.recoverAddress(hash, Numeric.toHexString(signature)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("invalid signature recovery id");
  }

  private String signatureHex(Sign.SignatureData signatureData) {
    byte[] bytes = new byte[65];
    System.arraycopy(signatureData.getR(), 0, bytes, 0, 32);
    System.arraycopy(signatureData.getS(), 0, bytes, 32, 32);
    bytes[64] = signatureData.getV()[0];
    return Numeric.toHexString(bytes);
  }
}
