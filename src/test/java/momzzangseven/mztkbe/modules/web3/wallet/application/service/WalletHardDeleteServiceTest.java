package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.wallet.application.config.WalletHardDeleteProperties;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort.WalletDeletionInfo;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for WalletHardDeleteService
 *
 * <p>Uses Mockito to isolate service logic from dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletHardDeleteService Unit Test")
class WalletHardDeleteServiceTest {

  @Mock private LoadWalletPort loadWalletPort;
  @Mock private DeleteWalletPort deleteWalletPort;
  @Mock private RecordWalletEventPort recordWalletEventPort;
  @Mock private WalletHardDeleteProperties props;

  @InjectMocks private WalletHardDeleteService walletHardDeleteService;

  private static final int VALID_RETENTION_DAYS = 30;
  private static final int VALID_BATCH_SIZE = 100;
  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
  private static final Long VALID_USER_ID = 1L;

  // ========================================
  // Scheduled Deletion (UNLINKED) Success Cases
  // ========================================

  @Nested
  @DisplayName("Scheduled Deletion (UNLINKED) Success Cases")
  class ScheduledDeletionSuccessCases {

    @Test
    @DisplayName("Successfully deletes UNLINKED wallets in batch")
    void runBatch_WithUnlinkedWallets_DeletesSuccessfully() {
      // Given
      when(props.getRetentionDays()).thenReturn(VALID_RETENTION_DAYS);
      when(props.getBatchSize()).thenReturn(VALID_BATCH_SIZE);

      Instant now = Instant.now();
      Instant cutoff = now.minus(VALID_RETENTION_DAYS, ChronoUnit.DAYS);

      List<WalletDeletionInfo> wallets =
          List.of(
              new WalletDeletionInfo(1L, VALID_WALLET_ADDRESS, VALID_USER_ID),
              new WalletDeletionInfo(2L, "0x" + "b".repeat(40), 2L),
              new WalletDeletionInfo(3L, "0x" + "c".repeat(40), 3L));

      when(loadWalletPort.loadWalletsForDeletion(cutoff, VALID_BATCH_SIZE)).thenReturn(wallets);

      // When
      int deleted = walletHardDeleteService.runBatch(now);

      // Then
      assertThat(deleted).isEqualTo(3);
      verify(loadWalletPort, times(1)).loadWalletsForDeletion(cutoff, VALID_BATCH_SIZE);
      verify(recordWalletEventPort, times(1)).recordBatch(anyList());
      verify(deleteWalletPort, times(1)).deleteAllByIdInBatch(argThat(ids -> ids.size() == 3));
    }

    @Test
    @DisplayName("Returns 0 when no wallets to delete")
    void runBatch_NoWallets_ReturnsZero() {
      // Given
      when(props.getRetentionDays()).thenReturn(VALID_RETENTION_DAYS);
      when(props.getBatchSize()).thenReturn(VALID_BATCH_SIZE);

      Instant now = Instant.now();
      Instant cutoff = now.minus(VALID_RETENTION_DAYS, ChronoUnit.DAYS);

      when(loadWalletPort.loadWalletsForDeletion(cutoff, VALID_BATCH_SIZE))
          .thenReturn(Collections.emptyList());

      // When
      int deleted = walletHardDeleteService.runBatch(now);

      // Then
      assertThat(deleted).isEqualTo(0);
      verify(recordWalletEventPort, never()).recordBatch(any());
      verify(deleteWalletPort, never()).deleteAllByIdInBatch(any());
    }

    @Test
    @DisplayName("Calculates cutoff date correctly")
    void runBatch_CalculatesCutoffCorrectly() {
      // Given
      when(props.getRetentionDays()).thenReturn(VALID_RETENTION_DAYS);
      when(props.getBatchSize()).thenReturn(VALID_BATCH_SIZE);

      Instant now = Instant.parse("2026-01-28T00:00:00Z");
      Instant expectedCutoff = now.minus(VALID_RETENTION_DAYS, ChronoUnit.DAYS);

      when(loadWalletPort.loadWalletsForDeletion(any(), anyInt()))
          .thenReturn(Collections.emptyList());

      // When
      walletHardDeleteService.runBatch(now);

      // Then
      verify(loadWalletPort, times(1))
          .loadWalletsForDeletion(eq(expectedCutoff), eq(VALID_BATCH_SIZE));
    }
  }

  // ========================================
  // Cascade Deletion (USER_DELETED) Success Cases
  // ========================================

  @Nested
  @DisplayName("Cascade Deletion (USER_DELETED) Success Cases")
  class CascadeDeletionSuccessCases {

    @Test
    @DisplayName("Successfully deletes USER_DELETED wallets by user IDs")
    void deleteByUserIds_WithUserDeletedWallets_DeletesSuccessfully() {
      // Given
      List<Long> userIds = List.of(1L, 2L, 3L);
      List<WalletDeletionInfo> wallets =
          List.of(
              new WalletDeletionInfo(10L, VALID_WALLET_ADDRESS, 1L),
              new WalletDeletionInfo(20L, "0x" + "b".repeat(40), 2L),
              new WalletDeletionInfo(30L, "0x" + "c".repeat(40), 3L));

      when(loadWalletPort.findWalletsByUserIdAndUserDeleted(userIds)).thenReturn(wallets);

      // When
      int deleted = walletHardDeleteService.deleteByUserIds(userIds);

      // Then
      assertThat(deleted).isEqualTo(3);
      verify(loadWalletPort, times(1)).findWalletsByUserIdAndUserDeleted(userIds);
      verify(recordWalletEventPort, times(1)).recordBatch(anyList());
      verify(deleteWalletPort, times(1)).deleteAllByIdInBatch(argThat(ids -> ids.size() == 3));
    }

    @Test
    @DisplayName("Returns 0 when no USER_DELETED wallets found")
    void deleteByUserIds_NoWallets_ReturnsZero() {
      // Given
      List<Long> userIds = List.of(1L, 2L);

      when(loadWalletPort.findWalletsByUserIdAndUserDeleted(userIds))
          .thenReturn(Collections.emptyList());

      // When
      int deleted = walletHardDeleteService.deleteByUserIds(userIds);

      // Then
      assertThat(deleted).isEqualTo(0);
      verify(recordWalletEventPort, never()).recordBatch(any());
      verify(deleteWalletPort, never()).deleteAllByIdInBatch(any());
    }

    @Test
    @DisplayName("Handles null user IDs gracefully")
    void deleteByUserIds_NullUserIds_ReturnsZero() {
      // When
      int deleted = walletHardDeleteService.deleteByUserIds(null);

      // Then
      assertThat(deleted).isEqualTo(0);
      verify(loadWalletPort, never()).findWalletsByUserIdAndUserDeleted(any());
    }

    @Test
    @DisplayName("Handles empty user IDs gracefully")
    void deleteByUserIds_EmptyUserIds_ReturnsZero() {
      // When
      int deleted = walletHardDeleteService.deleteByUserIds(Collections.emptyList());

      // Then
      assertThat(deleted).isEqualTo(0);
      verify(loadWalletPort, never()).findWalletsByUserIdAndUserDeleted(any());
    }
  }

  // ========================================
  // Configuration Validation Cases
  // ========================================

  @Nested
  @DisplayName("Configuration Validation Cases")
  class ConfigurationValidationCases {

    @Test
    @DisplayName("Zero retention days throws exception")
    void runBatch_ZeroRetentionDays_ThrowsException() {
      // Given
      when(props.getRetentionDays()).thenReturn(0);

      // When & Then
      assertThatThrownBy(() -> walletHardDeleteService.runBatch(Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("retentionDays must be greater than 0");

      verify(loadWalletPort, never()).loadWalletsForDeletion(any(), anyInt());
    }

    @Test
    @DisplayName("Negative retention days throws exception")
    void runBatch_NegativeRetentionDays_ThrowsException() {
      // Given
      when(props.getRetentionDays()).thenReturn(-1);

      // When & Then
      assertThatThrownBy(() -> walletHardDeleteService.runBatch(Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("retentionDays must be greater than 0");

      verify(loadWalletPort, never()).loadWalletsForDeletion(any(), anyInt());
    }

    @Test
    @DisplayName("Zero batch size throws exception")
    void runBatch_ZeroBatchSize_ThrowsException() {
      // Given
      when(props.getBatchSize()).thenReturn(0);
      when(props.getRetentionDays()).thenReturn(30);

      // When & Then
      assertThatThrownBy(() -> walletHardDeleteService.runBatch(Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchSize must be greater than 0");

      verify(loadWalletPort, never()).loadWalletsForDeletion(any(), anyInt());
    }

    @Test
    @DisplayName("Negative batch size throws exception")
    void runBatch_NegativeBatchSize_ThrowsException() {
      // Given
      when(props.getBatchSize()).thenReturn(-1);
      when(props.getRetentionDays()).thenReturn(30);

      // When & Then
      assertThatThrownBy(() -> walletHardDeleteService.runBatch(Instant.now()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchSize must be greater than 0");

      verify(loadWalletPort, never()).loadWalletsForDeletion(any(), anyInt());
    }
  }

  // ========================================
  // Event Recording Cases
  // ========================================

  @Nested
  @DisplayName("Event Recording Cases")
  class EventRecordingCases {

    @Test
    @DisplayName("Records HARD_DELETED events in batch for scheduled deletion")
    void runBatch_RecordsHardDeletedEvents() {
      // Given
      when(props.getRetentionDays()).thenReturn(VALID_RETENTION_DAYS);
      when(props.getBatchSize()).thenReturn(VALID_BATCH_SIZE);

      Instant now = Instant.now();
      Instant cutoff = now.minus(VALID_RETENTION_DAYS, ChronoUnit.DAYS);

      List<WalletDeletionInfo> wallets =
          List.of(
              new WalletDeletionInfo(1L, VALID_WALLET_ADDRESS, VALID_USER_ID),
              new WalletDeletionInfo(2L, "0x" + "b".repeat(40), 2L));

      when(loadWalletPort.loadWalletsForDeletion(cutoff, VALID_BATCH_SIZE)).thenReturn(wallets);

      // When
      walletHardDeleteService.runBatch(now);

      // Then
      verify(recordWalletEventPort, times(1)).recordBatch(argThat(events -> events.size() == 2));
    }

    @Test
    @DisplayName("Records HARD_DELETED events in batch for cascade deletion")
    void deleteByUserIds_RecordsHardDeletedEvents() {
      // Given
      List<Long> userIds = List.of(1L, 2L);
      List<WalletDeletionInfo> wallets =
          List.of(
              new WalletDeletionInfo(10L, VALID_WALLET_ADDRESS, 1L),
              new WalletDeletionInfo(20L, "0x" + "b".repeat(40), 2L));

      when(loadWalletPort.findWalletsByUserIdAndUserDeleted(userIds)).thenReturn(wallets);

      // When
      walletHardDeleteService.deleteByUserIds(userIds);

      // Then
      verify(recordWalletEventPort, times(1)).recordBatch(argThat(events -> events.size() == 2));
    }
  }

  // ========================================
  // Batch Deletion Order Cases
  // ========================================

  @Nested
  @DisplayName("Batch Deletion Order Cases")
  class BatchDeletionOrderCases {

    @Test
    @DisplayName("Events are recorded before wallets are deleted")
    void runBatch_RecordsEventsBeforeDeletion() {
      // Given
      when(props.getRetentionDays()).thenReturn(VALID_RETENTION_DAYS);
      when(props.getBatchSize()).thenReturn(VALID_BATCH_SIZE);

      Instant now = Instant.now();
      Instant cutoff = now.minus(VALID_RETENTION_DAYS, ChronoUnit.DAYS);

      List<WalletDeletionInfo> wallets =
          List.of(new WalletDeletionInfo(1L, VALID_WALLET_ADDRESS, VALID_USER_ID));

      when(loadWalletPort.loadWalletsForDeletion(cutoff, VALID_BATCH_SIZE)).thenReturn(wallets);

      // When
      walletHardDeleteService.runBatch(now);

      // Then
      var inOrder = inOrder(recordWalletEventPort, deleteWalletPort);
      inOrder.verify(recordWalletEventPort).recordBatch(anyList());
      inOrder.verify(deleteWalletPort).deleteAllByIdInBatch(anyList());
    }

    @Test
    @DisplayName("Events are recorded before wallets are deleted (cascade)")
    void deleteByUserIds_RecordsEventsBeforeDeletion() {
      // Given
      List<Long> userIds = List.of(1L);
      List<WalletDeletionInfo> wallets =
          List.of(new WalletDeletionInfo(10L, VALID_WALLET_ADDRESS, 1L));

      when(loadWalletPort.findWalletsByUserIdAndUserDeleted(userIds)).thenReturn(wallets);

      // When
      walletHardDeleteService.deleteByUserIds(userIds);

      // Then
      var inOrder = inOrder(recordWalletEventPort, deleteWalletPort);
      inOrder.verify(recordWalletEventPort).recordBatch(anyList());
      inOrder.verify(deleteWalletPort).deleteAllByIdInBatch(anyList());
    }
  }
}
