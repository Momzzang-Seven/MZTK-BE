package momzzangseven.mztkbe.modules.web3.challenge.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;

/**
 * Challenge domain model challenge for authentication in web3 interactions. Conform to EIP-4361
 * standard
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Challenge {

  private String nonce;
  private Long userId;
  private ChallengePurpose purpose;
  private String walletAddress;
  private String message;
  private ChallengeStatus status;
  private Instant expiresAt;
  private Instant createdAt;
  private Instant usedAt;

  /**
   * Create new challenge
   *
   * @param userId user ID
   * @param purpose purpose of creating new challenge
   * @param walletAddress wallet address
   * @param config challenge configuration (EIP-4361 parameters)
   * @return newly created challenge object
   */
  public static Challenge create(
      Long userId, ChallengePurpose purpose, String walletAddress, ChallengeConfig config) {
    Instant now = Instant.now();
    String nonce = generateNonce();
    String message =
        buildEIP4361Message(
            purpose,
            walletAddress,
            nonce,
            now,
            config.domain(),
            config.uri(),
            config.version(),
            config.chainId());

    return Challenge.builder()
        .nonce(nonce)
        .userId(userId)
        .purpose(purpose)
        .walletAddress(walletAddress.toLowerCase()) // normalization
        .message(message)
        .status(ChallengeStatus.PENDING)
        .expiresAt(now.plusSeconds(config.ttlSeconds()))
        .createdAt(now)
        .build();
  }

  /** Create nonce */
  private static String generateNonce() {
    return UUID.randomUUID().toString();
  }

  /**
   * Create EIP-4361 message Format: ${domain} wants you to ${action} with your Ethereum account:
   * ${address}
   *
   * @param purpose challenge purpose
   * @param address wallet address
   * @param nonce unique nonce
   * @param issuedAt timestamp
   * @param domain service domain
   * @param uri service URI
   * @param version EIP-4361 version
   * @param chainId blockchain chain ID
   * @return String EIP-4361 message
   */
  private static String buildEIP4361Message(
      ChallengePurpose purpose,
      String address,
      String nonce,
      Instant issuedAt,
      String domain,
      String uri,
      String version,
      String chainId) {
    String action = getActionDescription(purpose);

    return String.format(
        "%s wants you to %s with your Ethereum account:\n"
            + "%s\n\n"
            + "URI: %s\n"
            + "Version: %s\n"
            + "Chain ID: %s\n"
            + "Nonce: %s\n"
            + "Issued At: %s",
        domain, action, address, uri, version, chainId, nonce, issuedAt.toString());
  }

  /** Action description for each Purpose */
  private static String getActionDescription(ChallengePurpose purpose) {
    return switch (purpose) {
      case WALLET_REGISTRATION -> "register your wallet";
        // further added
    };
  }

  /** Check if challenge has expired */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /** Check if challenge has already been used */
  public boolean isUsed() {
    return status == ChallengeStatus.USED;
  }

  /** Check if challenge belongs to the given user */
  public boolean matchesUser(Long userId) {
    return this.userId.equals(userId);
  }

  /** Check if wallet address matches (case-insensitive) */
  public boolean matchesAddress(String walletAddress) {
    return this.walletAddress.equalsIgnoreCase(walletAddress);
  }

  /** Mark challenge as used */
  public Challenge markAsUsed() {
    return this.toBuilder().status(ChallengeStatus.USED).usedAt(Instant.now()).build();
  }

  /** Mark challenge as expired */
  public Challenge markAsExpired() {
    return this.toBuilder().status(ChallengeStatus.EXPIRED).build();
  }
}
