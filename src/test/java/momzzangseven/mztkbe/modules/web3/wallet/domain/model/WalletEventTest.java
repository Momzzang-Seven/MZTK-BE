package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for WalletEvent domain model
 *
 * <p>Tests factory methods for creating different types of wallet events.
 */
@DisplayName("WalletEvent Domain Test")
class WalletEventTest {

  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
  private static final Long VALID_USER_ID = 1L;
  private static final Long VALID_PREVIOUS_USER_ID = 2L;
  private static final Map<String, Object> VALID_METADATA =
      Map.of("source", "test", "action", "test_action");

  @Nested
  @DisplayName("registered() Factory Method")
  class RegisteredFactoryMethod {

    @Test
    @DisplayName("Creates REGISTERED event for new wallet")
    void registered_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event =
          WalletEvent.registered(VALID_WALLET_ADDRESS, VALID_USER_ID, VALID_METADATA);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(event.getEventType()).isEqualTo(WalletEventType.REGISTERED);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousUserId()).isNull();
      assertThat(event.getPreviousStatus()).isNull();
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(event.getMetadata()).isEqualTo(VALID_METADATA);
      assertThat(event.getOccurredAt()).isNotNull();
      assertThat(event.getOccurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Handles null metadata")
    void registered_NullMetadata_CreatesEvent() {
      // When
      WalletEvent event = WalletEvent.registered(VALID_WALLET_ADDRESS, VALID_USER_ID, null);

      // Then
      assertThat(event.getMetadata()).isNull();
    }
  }

  @Nested
  @DisplayName("reRegistered() Factory Method")
  class ReRegisteredFactoryMethod {

    @Test
    @DisplayName("Creates REGISTERED event for re-registration")
    void reRegistered_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event =
          WalletEvent.reRegistered(
              VALID_WALLET_ADDRESS,
              VALID_USER_ID,
              VALID_PREVIOUS_USER_ID,
              WalletStatus.UNLINKED,
              VALID_METADATA);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(event.getEventType()).isEqualTo(WalletEventType.REGISTERED);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousUserId()).isEqualTo(VALID_PREVIOUS_USER_ID);
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.UNLINKED);
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(event.getMetadata()).isEqualTo(VALID_METADATA);
    }

    @Test
    @DisplayName("Tracks ownership change from previous user to new user")
    void reRegistered_TracksOwnershipChange() {
      // When
      WalletEvent event =
          WalletEvent.reRegistered(
              VALID_WALLET_ADDRESS,
              VALID_USER_ID,
              VALID_PREVIOUS_USER_ID,
              WalletStatus.USER_DELETED,
              VALID_METADATA);

      // Then
      assertThat(event.getPreviousUserId()).isEqualTo(VALID_PREVIOUS_USER_ID);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.USER_DELETED);
    }
  }

  @Nested
  @DisplayName("unlinked() Factory Method")
  class UnlinkedFactoryMethod {

    @Test
    @DisplayName("Creates UNLINKED event")
    void unlinked_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event = WalletEvent.unlinked(VALID_WALLET_ADDRESS, VALID_USER_ID, VALID_METADATA);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(event.getEventType()).isEqualTo(WalletEventType.UNLINKED);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.UNLINKED);
      assertThat(event.getMetadata()).isEqualTo(VALID_METADATA);
    }
  }

  @Nested
  @DisplayName("userDeleted() Factory Method")
  class UserDeletedFactoryMethod {

    @Test
    @DisplayName("Creates USER_DELETED event")
    void userDeleted_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event =
          WalletEvent.userDeleted(VALID_WALLET_ADDRESS, VALID_USER_ID, VALID_METADATA);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(event.getEventType()).isEqualTo(WalletEventType.USER_DELETED);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.USER_DELETED);
      assertThat(event.getMetadata()).isEqualTo(VALID_METADATA);
    }
  }

  @Nested
  @DisplayName("hardDeleted() Factory Method")
  class HardDeletedFactoryMethod {

    @Test
    @DisplayName("Creates HARD_DELETED event from UNLINKED")
    void hardDeleted_FromUnlinked_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event =
          WalletEvent.hardDeleted(
              VALID_WALLET_ADDRESS, VALID_USER_ID, WalletStatus.UNLINKED, VALID_METADATA);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(event.getEventType()).isEqualTo(WalletEventType.HARD_DELETED);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.UNLINKED);
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.HARD_DELETED);
      assertThat(event.getMetadata()).isEqualTo(VALID_METADATA);
    }

    @Test
    @DisplayName("Creates HARD_DELETED event from USER_DELETED")
    void hardDeleted_FromUserDeleted_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event =
          WalletEvent.hardDeleted(
              VALID_WALLET_ADDRESS, VALID_USER_ID, WalletStatus.USER_DELETED, VALID_METADATA);

      // Then
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.USER_DELETED);
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.HARD_DELETED);
    }
  }

  @Nested
  @DisplayName("blocked() Factory Method")
  class BlockedFactoryMethod {

    @Test
    @DisplayName("Creates BLOCKED event")
    void blocked_CreatesEventWithCorrectFields() {
      // When
      WalletEvent event =
          WalletEvent.blocked(
              VALID_WALLET_ADDRESS, VALID_USER_ID, WalletStatus.ACTIVE, VALID_METADATA);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getWalletAddress()).isEqualTo(VALID_WALLET_ADDRESS);
      assertThat(event.getEventType()).isEqualTo(WalletEventType.BLOCKED);
      assertThat(event.getUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousUserId()).isEqualTo(VALID_USER_ID);
      assertThat(event.getPreviousStatus()).isEqualTo(WalletStatus.ACTIVE);
      assertThat(event.getNewStatus()).isEqualTo(WalletStatus.BLOCKED);
      assertThat(event.getMetadata()).isEqualTo(VALID_METADATA);
    }
  }
}
