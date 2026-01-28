package momzzangseven.mztkbe.modules.web3.signature.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.signature.application.port.out.VerifySignaturePort;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.EIP712Domain;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.EIP712Message;
import momzzangseven.mztkbe.modules.web3.signature.domain.model.TypedData;
import org.springframework.stereotype.Component;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * EIP-712 Signature Verifier Implementation
 *
 * <p>Verifies EIP-712 signatures using Web3j.
 *
 * <p>Verification process: 1. Structure TypedData (Domain + Message) 2. Calculate Domain Separator
 * 3. Calculate Message Hash 4. Calculate EIP-712 Digest: keccak256("\x19\x01" ‖ domainSeparator ‖
 * messageHash) 5. Recover signer address using ecrecover 6. Compare recovered address with expected
 * address
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EIP712SignatureVerifier implements VerifySignaturePort {
  private final EIP712Domain eip712Domain;
  private final ObjectMapper objectMapper;

  @Override
  public boolean verify(
      String challengeMessage, String nonce, String signature, String expectedAddress) {
    try {
      // 1. Validate inputs
      validateInputs(challengeMessage, nonce, signature, expectedAddress);

      // 2. Build TypedData
      EIP712Message message =
          EIP712Message.builder().content(challengeMessage).nonce(nonce).build();

      TypedData typedData = TypedData.forWalletRegistration(eip712Domain, message);

      // 3. Convert to EIP-712 JSON and calculate digest using Web3j
      String jsonTypedData = convertToEIP712Json(typedData);
      StructuredDataEncoder encoder = new StructuredDataEncoder(jsonTypedData);
      byte[] digest = encoder.hashStructuredData();

      // 4. Recover signer address from signature
      String recoveredAddress = recoverAddress(digest, signature);

      // 5. Compare addresses (case-insensitive)
      boolean isValid = recoveredAddress.equalsIgnoreCase(expectedAddress);

      if (isValid) {
        log.info(
            "Signature verification successful: expected={}, recovered={}",
            expectedAddress,
            recoveredAddress);
      } else {
        log.warn(
            "Signature verification failed: expected={}, recovered={}",
            expectedAddress,
            recoveredAddress);
      }

      return isValid;

    } catch (Exception e) {
      log.error("Signature verification error: {}", e.getMessage(), e);
      return false;
    }
  }

  /** Validate input parameters */
  private void validateInputs(String message, String nonce, String signature, String address) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Message must not be blank");
    }
    if (nonce == null || nonce.isBlank()) {
      throw new IllegalArgumentException("Nonce must not be blank");
    }
    if (signature == null || !signature.matches("^0x[0-9a-fA-F]{130}$")) {
      throw new IllegalArgumentException("Invalid signature format (expected: 0x + 130 hex chars)");
    }
    if (address == null || !address.matches("^0x[0-9a-fA-F]{40}$")) {
      throw new IllegalArgumentException("Invalid address format (expected: 0x + 40 hex chars)");
    }
  }

  /**
   * Convert TypedData to EIP-712 JSON format
   *
   * <p>Web3j's StructuredDataEncoder requires JSON string input. This method converts our domain
   * models to the exact JSON format required by EIP-712.
   *
   * @param typedData domain model containing all EIP-712 data
   * @return JSON string in EIP-712 format
   * @throws JsonProcessingException if JSON serialization fails
   */
  private String convertToEIP712Json(TypedData typedData) throws JsonProcessingException {
    Map<String, Object> eip712Json = new LinkedHashMap<>();

    // types
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
    eip712Json.put("types", types);

    // primaryType
    eip712Json.put("primaryType", typedData.getPrimaryType());

    // domain
    Map<String, Object> domain = new LinkedHashMap<>();
    domain.put("name", typedData.getDomain().getName());
    domain.put("version", typedData.getDomain().getVersion());
    domain.put("chainId", typedData.getDomain().getChainId());
    domain.put("verifyingContract", typedData.getDomain().getVerifyingContract());
    eip712Json.put("domain", domain);

    // message
    Map<String, String> message = new LinkedHashMap<>();
    message.put("content", typedData.getMessage().getContent());
    message.put("nonce", typedData.getMessage().getNonce());
    eip712Json.put("message", message);

    return objectMapper.writeValueAsString(eip712Json);
  }

  /**
   * Recover signer address from signature using ecrecover
   *
   * @param digest EIP-712 digest
   * @param signatureHex signature in hex format (0x + r + s + v)
   * @return recovered Ethereum address (0x-prefixed, lowercase)
   */
  private String recoverAddress(byte[] digest, String signatureHex) {
    try {
      // Remove "0x" prefix
      String signatureWithoutPrefix = Numeric.cleanHexPrefix(signatureHex);

      // Extract r, s, v from signature
      // Signature format: r (32 bytes) + s (32 bytes) + v (1 byte) = 65 bytes = 130 hex chars
      byte[] signatureBytes = Numeric.hexStringToByteArray(signatureWithoutPrefix);

      if (signatureBytes.length != 65) {
        throw new IllegalArgumentException(
            "Invalid signature length: " + signatureBytes.length + " (expected 65 bytes)");
      }

      byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
      byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
      byte v = signatureBytes[64];

      // Normalize v (should be 27 or 28, but MetaMask might send 0 or 1)
      if (v < 27) {
        v += 27;
      }

      // Create ECDSA signature
      ECDSASignature ecdsaSignature =
          new ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));

      // Recover public key
      BigInteger publicKey = Sign.recoverFromSignature(v - 27, ecdsaSignature, digest);

      if (publicKey == null) {
        throw new RuntimeException("Could not recover public key from signature");
      }

      // Derive address from public key
      String address = "0x" + Keys.getAddress(publicKey);

      log.debug("Recovered address: {}", address);

      return address.toLowerCase();

    } catch (Exception e) {
      log.error("Address recovery failed: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to recover address from signature", e);
    }
  }
}
