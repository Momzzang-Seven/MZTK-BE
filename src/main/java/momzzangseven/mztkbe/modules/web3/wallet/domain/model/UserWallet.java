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
  private Instant unlinkedAt;
  private Instant userDeletedAt;

  /**
   * Create new wallet registration
   *
   * @param userId user ID
   * @param walletAddress Ethereum address (should be already normalized to lowercase)
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

    return UserWallet.builder()
        .userId(userId)
        .walletAddress(walletAddress)
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

  /** Deactivate wallet
   * @return new UserWallet instance with UNLINKED status
   * */
  public UserWallet unlink() {
    return this.toBuilder()
            .status(WalletStatus.UNLINKED)
            .unlinkedAt(Instant.now())
            .build();
  }

  /** Mark as user deleted
   * @return new UserWallet instance with USER_DELETED status*/
  public UserWallet markAsUserDeleted() { return this.toBuilder()
          .status(WalletStatus.USER_DELETED)
          .userDeletedAt(Instant.now())
          .build(); }


  /** Blacklist wallet
   * @return new UserWallet instance with BLOCKED status*/
  public UserWallet block() {
    return this.toBuilder().status(WalletStatus.BLOCKED).build();
  }

  /** Check if wallet can be re-registered
   * @return  true if wallet can be re-registered
   * */
  public boolean canBeReRegistered() { return status == WalletStatus.UNLINKED || status == WalletStatus.USER_DELETED; }
}
