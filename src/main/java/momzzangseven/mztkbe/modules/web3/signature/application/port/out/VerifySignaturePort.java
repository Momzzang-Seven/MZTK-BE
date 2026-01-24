package momzzangseven.mztkbe.modules.web3.signature.application.port.out;

/** Port for verifying EIP-712 signatures. */
public interface VerifySignaturePort {
  /**
   * Verify EIP-712 signature
   *
   * @param challengeMessage challenge message content (used in EIP712Message.content)
   * @param nonce challenge nonce
   * @param signature hex-encoded signature (0x-prefixed, 130 chars)
   * @param expectedAddress expected signer address (0x-prefixed, 40 chars)
   * @return true if signature is valid and recovered address matches expectedAddress
   */
  boolean verify(String challengeMessage, String nonce, String signature, String expectedAddress);
}
