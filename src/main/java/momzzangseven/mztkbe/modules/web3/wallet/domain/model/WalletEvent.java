package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WalletEvent {
  private Long id;
  private String walletAddress;
  private WalletEventType eventType;
  private Long userId;
  private Long previousUserId;
  private WalletStatus previousStatus;
  private WalletStatus newStatus;
  private Map<String, Object> metadata;
  private Instant occurredAt;

  /** Create REGISTERED event when fresh registered */
  public static WalletEvent registered(
      String walletAddress, Long userId, Map<String, Object> metadata) {
    return WalletEvent.builder()
        .walletAddress(walletAddress)
        .eventType(WalletEventType.REGISTERED)
        .userId(userId)
        .previousUserId(null)
        .previousStatus(null)
        .newStatus(WalletStatus.ACTIVE)
        .metadata(metadata)
        .occurredAt(Instant.now())
        .build();
  }

  /** Create REGISTERED event when re-registered */
  public static WalletEvent reRegistered(
      String walletAddress,
      Long userId,
      Long previousUserId,
      WalletStatus previousStatus,
      Map<String, Object> metadata) {
    return WalletEvent.builder()
        .walletAddress(walletAddress)
        .eventType(WalletEventType.REGISTERED)
        .userId(userId)
        .previousUserId(previousUserId)
        .previousStatus(previousStatus)
        .newStatus(WalletStatus.ACTIVE)
        .metadata(metadata)
        .occurredAt(Instant.now())
        .build();
  }

  /** Create UNLINKED event */
  public static WalletEvent unlinked(
      String walletAddress, Long userId, Map<String, Object> metadata) {
    return WalletEvent.builder()
        .walletAddress(walletAddress)
        .eventType(WalletEventType.UNLINKED)
        .userId(userId)
        .previousUserId(userId)
        .previousStatus(WalletStatus.ACTIVE)
        .newStatus(WalletStatus.UNLINKED)
        .metadata(metadata)
        .occurredAt(Instant.now())
        .build();
  }

  /** Create USER_DELETED event */
  public static WalletEvent userDeleted(
      String walletAddress, Long userId, Map<String, Object> metadata) {
    return WalletEvent.builder()
        .walletAddress(walletAddress)
        .eventType(WalletEventType.USER_DELETED)
        .userId(userId)
        .previousUserId(userId)
        .previousStatus(WalletStatus.ACTIVE)
        .newStatus(WalletStatus.USER_DELETED)
        .metadata(metadata)
        .occurredAt(Instant.now())
        .build();
  }

  /** Create HARD_DELETED event */
  public static WalletEvent hardDeleted(
      String walletAddress,
      Long userId,
      WalletStatus previousStatus,
      Map<String, Object> metadata) {
    return WalletEvent.builder()
        .walletAddress(walletAddress)
        .eventType(WalletEventType.HARD_DELETED)
        .userId(userId)
        .previousUserId(userId)
        .previousStatus(previousStatus)
        .newStatus(WalletStatus.HARD_DELETED)
        .metadata(metadata)
        .occurredAt(Instant.now())
        .build();
  }

  /** Create BLOCKED event */
  public static WalletEvent blocked(
      String walletAddress,
      Long userId,
      WalletStatus previousStatus,
      Map<String, Object> metadata) {
    return WalletEvent.builder()
        .walletAddress(walletAddress)
        .eventType(WalletEventType.BLOCKED)
        .userId(userId)
        .previousUserId(userId)
        .previousStatus(previousStatus)
        .newStatus(WalletStatus.BLOCKED)
        .metadata(metadata)
        .occurredAt(Instant.now())
        .build();
  }
}
