package momzzangseven.mztkbe.modules.web3.challenge.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.web3.challenge.infrastructure.config.ChallengeProperties;

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
  private LocalDateTime expiresAt;
  private LocalDateTime createdAt;
  private LocalDateTime usedAt;

  private final ChallengeProperties challengeProperties;

  /**
   * Create new challenge
   *
   * @param userId user ID
   * @param purpose purpose of creating new challenge
   * @param walletAddress wallet address
   * @return newly created challenge object.
   */
  public Challenge create(Long userId, ChallengePurpose purpose, String walletAddress) {
    LocalDateTime now = LocalDateTime.now();
    String nonce = generateNonce();
    String message = buildEIP4361Message(purpose, walletAddress, nonce, now);

    return Challenge.builder()
        .nonce(nonce)
        .userId(userId)
        .purpose(purpose)
        .walletAddress(walletAddress.toLowerCase()) // normalization
        .message(message)
        .status(ChallengeStatus.PENDING)
        .expiresAt(now.plusSeconds((getTtlSeconds())))
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
   * @param purpose
   * @param address
   * @param nonce
   * @param issuedAt
   * @return String EIP-4361 message
   */
  private String buildEIP4361Message(
      ChallengePurpose purpose, String address, String nonce, LocalDateTime issuedAt) {
    ChallengeProperties.Eip4361 eip4361 = challengeProperties.getEip4361();
    String action = getActionDescription(purpose);

    return String.format(
        "%s wants you to %s with your Ethereum account:\n"
            + "%s\n\n"
            + "URI: %s\n"
            + "Version: %s\n"
            + "Chain ID: %s\n"
            + "Nonce: %s\n"
            + "Issued At: %s",
        eip4361.getDomain(),
        action,
        address,
        eip4361.getUri(),
        eip4361.getVersion(),
        eip4361.getChainId(),
        nonce,
        issuedAt.toString());
  }

  /** Action description for each Purpose */
  private String getActionDescription(ChallengePurpose purpose) {
    return switch (purpose) {
      case WALLET_REGISTRATION -> "register your wallet";
        // further added
    };
  }

  /** Get Challenge TTL (in seconds) */
  private int getTtlSeconds() {
    return challengeProperties.getTtlSeconds();
  }

  /** Check if challenge has expired */
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
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
    return this.toBuilder().status(ChallengeStatus.USED).usedAt(LocalDateTime.now()).build();
  }

  /** Mark challenge as expired */
  public Challenge markAsExpired() {
    return this.toBuilder().status(ChallengeStatus.EXPIRED).build();
  }
}
