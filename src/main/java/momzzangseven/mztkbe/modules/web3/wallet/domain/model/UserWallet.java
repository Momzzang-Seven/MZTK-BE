package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * UserWallet domain model
 *
 * <p>Represents a Web3 wallet connected to a user account.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserWallet {

  private Long id;
  private Long userId;
  private String walletAddress;
  private WalletStatus status;
  private Instant registeredAt;

  /**
   * Create new wallet registration
   *
   * @param userId user ID
   * @param walletAddress Ethereum address (normalized to lowercase)
   * @param registeredAt timestamp when wallet is registered
   * @return new UserWallet instance
   */
  public static UserWallet create(Long userId, String walletAddress, Instant registeredAt) {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID must be positive");
    }
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new IllegalArgumentException("Wallet address must not be blank");
    }
    if (registeredAt == null) {
      throw new IllegalArgumentException("Registered timestamp must not be null");
    }

    // Normalize address to lowercase for consistency
    String normalizedAddress = walletAddress.toLowerCase();

    return UserWallet.builder()
        .userId(userId)
        .walletAddress(normalizedAddress)
        .status(WalletStatus.ACTIVE)
        .registeredAt(registeredAt)
        .build();
  }

  /** Check if wallet belongs to the given user */
  public boolean belongsTo(Long userId) {
    return this.userId.equals(userId);
  }

  /** Check if wallet is active */
  public boolean isActive() {
    return status == WalletStatus.ACTIVE;
  }

  /** Deactivate wallet (soft delete) */
  public UserWallet deactivate() {
    return this.toBuilder().status(WalletStatus.INACTIVE).build();
  }

  /** Blacklist wallet */
  public UserWallet blacklist() {
    return this.toBuilder().status(WalletStatus.BLACKLISTED).build();
  }
}
