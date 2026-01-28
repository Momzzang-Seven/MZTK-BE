package momzzangseven.mztkbe.modules.web3.signature.domain.vo;

/**
 * EIP-712 Type Definitions
 *
 * <p>Provides identical type definitions as frontend.
 */
public class EIP712Type {
  private EIP712Type() {
    // Utility class
  }

  /**
   * EIP712Domain type definition
   *
   * <p>JSON equivalent:
   *
   * <pre>
   * "EIP712Domain": [
   *   { "name": "name", "type": "string" },
   *   { "name": "version", "type": "string" },
   *   { "name": "chainId", "type": "uint256" },
   *   { "name": "verifyingContract", "type": "address" }
   * ]
   * </pre>
   */
  public static final String EIP712_DOMAIN_TYPE =
      "EIP712Domain("
          + "string name,"
          + "string version,"
          + "uint256 chainId,"
          + "address verifyingContract"
          + ")";

  /**
   * AuthRequest type definition
   *
   * <p>JSON equivalent:
   *
   * <pre>
   * "AuthRequest": [
   *   { "name": "content", "type": "string" },
   *   { "name": "nonce", "type": "string" }
   * ]
   * </pre>
   */
  public static final String AUTH_REQUEST_TYPE =
      "AuthRequest(" + "string content," + "string nonce" + ")";

  /**
   * Full type hash for AuthRequest
   *
   * <p>TypeHash = keccak256("AuthRequest(string content,string nonce)")
   */
  public static final String FULL_AUTH_REQUEST_TYPE = AUTH_REQUEST_TYPE;
}
