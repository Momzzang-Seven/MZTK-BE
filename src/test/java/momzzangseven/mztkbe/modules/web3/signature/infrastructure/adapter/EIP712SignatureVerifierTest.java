package momzzangseven.mztkbe.modules.web3.signature.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.EIP712Domain;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.EIP712Message;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.TypedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

class EIP712SignatureVerifierTest {

  private static final String PRIVATE_KEY = "0x" + "1".repeat(64);

  private EIP712Domain domain;
  private EIP712SignatureVerifier verifier;
  private String expectedAddress;

  @BeforeEach
  void setUp() {
    domain =
        EIP712Domain.builder()
            .name("MZTK")
            .version("1")
            .chainId(11155111L)
            .verifyingContract("0x" + "a".repeat(40))
            .build();
    verifier = new EIP712SignatureVerifier(domain, new ObjectMapper());

    ECKeyPair keyPair = ECKeyPair.create(Numeric.toBigInt(PRIVATE_KEY));
    expectedAddress = "0x" + Keys.getAddress(keyPair.getPublicKey());
  }

  @Test
  void verify_returnsTrue_whenSignatureAndAddressAreValid() throws Exception {
    String message = "sign-in challenge";
    String nonce = "nonce-123";
    String signature = signTypedData(message, nonce, false);

    boolean verified = verifier.verify(message, nonce, signature, expectedAddress);

    assertThat(verified).isTrue();
  }

  @Test
  void verify_returnsTrue_whenSignatureVIsNormalizedFromZeroOrOne() throws Exception {
    String message = "wallet register";
    String nonce = "nonce-456";
    String signature = signTypedData(message, nonce, true);

    boolean verified = verifier.verify(message, nonce, signature, expectedAddress);

    assertThat(verified).isTrue();
  }

  @Test
  void verify_returnsFalse_whenRecoveredAddressDiffers() throws Exception {
    String message = "challenge";
    String nonce = "nonce-789";
    String signature = signTypedData(message, nonce, false);
    String differentAddress = "0x" + "f".repeat(40);

    boolean verified = verifier.verify(message, nonce, signature, differentAddress);

    assertThat(verified).isFalse();
  }

  @Test
  void verify_returnsFalse_whenSignatureFormatInvalid() {
    boolean verified = verifier.verify("challenge", "nonce", "0x1234", expectedAddress);

    assertThat(verified).isFalse();
  }

  @Test
  void verify_returnsFalse_whenInputMessageBlank() {
    boolean verified = verifier.verify(" ", "nonce", "0x" + "0".repeat(130), "0x" + "a".repeat(40));

    assertThat(verified).isFalse();
  }

  private String signTypedData(String message, String nonce, boolean normalizeVToZeroOrOne)
      throws Exception {
    EIP712Message eip712Message = EIP712Message.builder().content(message).nonce(nonce).build();
    TypedData typedData = TypedData.forWalletRegistration(domain, eip712Message);
    String typedDataJson = toEip712Json(typedData);
    byte[] digest = new StructuredDataEncoder(typedDataJson).hashStructuredData();

    ECKeyPair keyPair = ECKeyPair.create(Numeric.toBigInt(PRIVATE_KEY));
    Sign.SignatureData signatureData = Sign.signMessage(digest, keyPair, false);
    byte v = signatureData.getV()[0];
    if (normalizeVToZeroOrOne) {
      v = (byte) (v - 27);
    }

    String rHex =
        Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, signatureData.getR()), 64);
    String sHex =
        Numeric.toHexStringNoPrefixZeroPadded(new BigInteger(1, signatureData.getS()), 64);
    String vHex = String.format("%02x", Byte.toUnsignedInt(v));
    return "0x" + rHex + sHex + vHex;
  }

  private String toEip712Json(TypedData typedData) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();

    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, List<Map<String, String>>> types = new LinkedHashMap<>();
    types.put(
        "EIP712Domain",
        List.of(
            Map.of("name", "name", "type", "string"),
            Map.of("name", "version", "type", "string"),
            Map.of("name", "chainId", "type", "uint256"),
            Map.of("name", "verifyingContract", "type", "address")));
    types.put(
        "AuthRequest",
        List.of(
            Map.of("name", "content", "type", "string"),
            Map.of("name", "nonce", "type", "string")));

    root.put("types", types);
    root.put("primaryType", typedData.getPrimaryType());
    root.put(
        "domain",
        Map.of(
            "name",
            typedData.getDomain().getName(),
            "version",
            typedData.getDomain().getVersion(),
            "chainId",
            typedData.getDomain().getChainId(),
            "verifyingContract",
            typedData.getDomain().getVerifyingContract()));
    root.put(
        "message",
        Map.of(
            "content",
            typedData.getMessage().getContent(),
            "nonce",
            typedData.getMessage().getNonce()));

    return mapper.writeValueAsString(root);
  }
}
