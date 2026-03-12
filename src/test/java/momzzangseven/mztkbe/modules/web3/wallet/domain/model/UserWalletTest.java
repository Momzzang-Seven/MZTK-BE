package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for UserWallet domain model
 *
 * <p>Tests the static factory method and domain logic without external dependencies.
 */
@DisplayName("UserWallet Domain Test")
class UserWalletTest {

  private static final Long VALID_USER_ID = 1L;
  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
  private static final Instant VALID_TIMESTAMP = Instant.now();

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("Valid wallet creates successfully")
    void create_ValidInputs_CreatesWallet() {
      // Given & When
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // Then
      assertThat(wallet).isNotNull();
      assertThat(wallet.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(wallet.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(wallet.getRegisteredAt()).isEqualTo(VALID_TIMESTAMP);
    }

    @Test
    @DisplayName("belongsTo returns true for matching userId")
    void belongsTo_MatchingUserId_ReturnsTrue() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When & Then
      assertThat(wallet.belongsTo(VALID_USER_ID)).isTrue();
    }

    @Test
    @DisplayName("belongsTo returns false for different userId")
    void belongsTo_DifferentUserId_ReturnsFalse() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When & Then
      assertThat(wallet.belongsTo(999L)).isFalse();
    }

    @Test
    @DisplayName("isActive returns true for ACTIVE status")
    void isActive_ActiveStatus_ReturnsTrue() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When & Then
      assertThat(wallet.isActive()).isTrue();
    }

    @Test
    @DisplayName("isActive returns false after deactivation")
    void isActive_AfterDeactivation_ReturnsFalse() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet deactivated = wallet.unlink();

      // Then
      assertThat(deactivated.isActive()).isFalse();
    }

    @Test
    @DisplayName("unlink changes status to UNLINKED")
    void unlink_ChangesStatusToUnlinked() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet unlinked = wallet.unlink();

      // Then
      assertThat(unlinked.getStatus()).isEqualTo(WalletStatus.UNLINKED);
      assertThat(unlinked.isActive()).isFalse();
      assertThat(unlinked.getUnlinkedAt()).isNotNull();
      assertThat(unlinked.getUnlinkedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("unlink preserves other fields")
    void unlink_PreservesOtherFields() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet unlinked = wallet.unlink();

      // Then
      assertThat(unlinked.getUserId()).isEqualTo(wallet.getUserId());
      assertThat(unlinked.getWalletAddress()).isEqualTo(wallet.getWalletAddress());
      assertThat(unlinked.getRegisteredAt()).isEqualTo(wallet.getRegisteredAt());
    }

    @Test
    @DisplayName("unlink returns new instance")
    void unlink_ReturnsNewInstance() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet unlinked = wallet.unlink();

      // Then
      assertThat(unlinked).isNotSameAs(wallet);
      assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE); // Original unchanged
    }

    @Test
    @DisplayName("markAsUserDeleted changes status to USER_DELETED")
    void markAsUserDeleted_ChangesStatusToUserDeleted() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet userDeleted = wallet.markAsUserDeleted();

      // Then
      assertThat(userDeleted.getStatus()).isEqualTo(WalletStatus.USER_DELETED);
      assertThat(userDeleted.isActive()).isFalse();
      assertThat(userDeleted.getUserDeletedAt()).isNotNull();
      assertThat(userDeleted.getUserDeletedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("markAsUserDeleted preserves other fields")
    void markAsUserDeleted_PreservesOtherFields() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet userDeleted = wallet.markAsUserDeleted();

      // Then
      assertThat(userDeleted.getUserId()).isEqualTo(wallet.getUserId());
      assertThat(userDeleted.getWalletAddress()).isEqualTo(wallet.getWalletAddress());
      assertThat(userDeleted.getRegisteredAt()).isEqualTo(wallet.getRegisteredAt());
    }

    @Test
    @DisplayName("block changes status to BLOCKED")
    void block_ChangesStatusToBlocked() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet blocked = wallet.block();

      // Then
      assertThat(blocked.getStatus()).isEqualTo(WalletStatus.BLOCKED);
      assertThat(blocked.isActive()).isFalse();
    }

    @Test
    @DisplayName("block preserves other fields")
    void block_PreservesOtherFields() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When
      UserWallet blocked = wallet.block();

      // Then
      assertThat(blocked.getUserId()).isEqualTo(wallet.getUserId());
      assertThat(blocked.getWalletAddress()).isEqualTo(wallet.getWalletAddress());
      assertThat(blocked.getRegisteredAt()).isEqualTo(wallet.getRegisteredAt());
    }

    @Test
    @DisplayName("canBeReRegistered returns true for UNLINKED")
    void canBeReRegistered_UnlinkedWallet_ReturnsTrue() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);
      UserWallet unlinked = wallet.unlink();

      // When & Then
      assertThat(unlinked.canBeReRegistered()).isTrue();
    }

    @Test
    @DisplayName("canBeReRegistered returns true for USER_DELETED")
    void canBeReRegistered_UserDeletedWallet_ReturnsTrue() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);
      UserWallet userDeleted = wallet.markAsUserDeleted();

      // When & Then
      assertThat(userDeleted.canBeReRegistered()).isTrue();
    }

    @Test
    @DisplayName("canBeReRegistered returns false for ACTIVE")
    void canBeReRegistered_ActiveWallet_ReturnsFalse() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);

      // When & Then
      assertThat(wallet.canBeReRegistered()).isFalse();
    }

    @Test
    @DisplayName("canBeReRegistered returns false for BLOCKED")
    void canBeReRegistered_BlockedWallet_ReturnsFalse() {
      // Given
      UserWallet wallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_TIMESTAMP);
      UserWallet blocked = wallet.block();

      // When & Then
      assertThat(blocked.canBeReRegistered()).isFalse();
    }
  }

  // ========================================
  // Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Validation Failure Cases")
  class ValidationFailureCases {

    @Test
    @DisplayName("Null userId throws exception")
    void create_NullUserId_ThrowsException() {
      // Given
      Long nullUserId = null;

      // When & Then
      assertThatThrownBy(() -> UserWallet.create(nullUserId, VALID_WALLET_ADDRESS, VALID_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");
    }

    @Test
    @DisplayName("Zero userId throws exception")
    void create_ZeroUserId_ThrowsException() {
      // Given
      Long zeroUserId = 0L;

      // When & Then
      assertThatThrownBy(() -> UserWallet.create(zeroUserId, VALID_WALLET_ADDRESS, VALID_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");
    }

    @Test
    @DisplayName("Negative userId throws exception")
    void create_NegativeUserId_ThrowsException() {
      // Given
      Long negativeUserId = -1L;

      // When & Then
      assertThatThrownBy(
              () -> UserWallet.create(negativeUserId, VALID_WALLET_ADDRESS, VALID_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");
    }

    @Test
    @DisplayName("Null wallet address throws exception")
    void create_NullWalletAddress_ThrowsException() {
      // Given
      String nullAddress = null;

      // When & Then
      assertThatThrownBy(() -> UserWallet.create(VALID_USER_ID, nullAddress, VALID_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");
    }

    @Test
    @DisplayName("Empty wallet address throws exception")
    void create_EmptyWalletAddress_ThrowsException() {
      // Given
      String emptyAddress = "";

      // When & Then
      assertThatThrownBy(() -> UserWallet.create(VALID_USER_ID, emptyAddress, VALID_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");
    }

    @Test
    @DisplayName("Blank wallet address throws exception")
    void create_BlankWalletAddress_ThrowsException() {
      // Given
      String blankAddress = "   ";

      // When & Then
      assertThatThrownBy(() -> UserWallet.create(VALID_USER_ID, blankAddress, VALID_TIMESTAMP))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");
    }

    @Test
    @DisplayName("Null registered timestamp throws exception")
    void create_NullTimestamp_ThrowsException() {
      // Given
      Instant nullTimestamp = null;

      // When & Then
      assertThatThrownBy(
              () -> UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, nullTimestamp))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Registered timestamp must not be null");
    }
  }
}
