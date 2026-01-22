package momzzangseven.mztkbe.modules.web3.challenge.domain.vo;

/**
 * Challenge configuration value object
 *
 * <p>Encapsulates EIP-4361 configuration parameters needed for challenge creation.
 */
public record ChallengeConfig(
    int ttlSeconds, String domain, String uri, String version, String chainId) {

  /**
   * Create configuration
   *
   * @param ttlSeconds time-to-live in seconds
   * @param domain service domain name
   * @param uri service URI
   * @param version EIP-4361 version
   * @param chainId blockchain chain ID
   */
  public ChallengeConfig {
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("TTL must be positive");
    }
    if (domain == null || domain.isBlank()) {
      throw new IllegalArgumentException("Domain must not be blank");
    }
    if (uri == null || uri.isBlank()) {
      throw new IllegalArgumentException("URI must not be blank");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Version must not be blank");
    }
    if (chainId == null || chainId.isBlank()) {
      throw new IllegalArgumentException("Chain ID must not be blank");
    }
  }
}
