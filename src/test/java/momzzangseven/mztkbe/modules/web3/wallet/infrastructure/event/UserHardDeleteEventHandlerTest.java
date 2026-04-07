package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.event.UsersHardDeletedEvent;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.WalletHardDeleteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for UserHardDeleteEventHandler
 *
 * <p>Tests the event handler's behavior using Mockito to isolate from dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserHardDeleteEventHandler Unit Test")
class UserHardDeleteEventHandlerTest {

  @Mock private WalletHardDeleteService walletHardDeleteService;

  @InjectMocks private WalletUserHardDeleteEventHandler eventHandler;

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("Successfully handles event with single user")
    void handleUsersHardDeleted_SingleUser_CallsService() {
      // Given
      List<Long> userIds = List.of(1L);
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(userIds);

      when(walletHardDeleteService.deleteByUserIds(userIds)).thenReturn(1);

      // When
      eventHandler.handleUsersHardDeleted(event);

      // Then
      verify(walletHardDeleteService, times(1)).deleteByUserIds(userIds);
    }

    @Test
    @DisplayName("Successfully handles event with multiple users")
    void handleUsersHardDeleted_MultipleUsers_CallsService() {
      // Given
      List<Long> userIds = List.of(1L, 2L, 3L);
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(userIds);

      when(walletHardDeleteService.deleteByUserIds(userIds)).thenReturn(5);

      // When
      eventHandler.handleUsersHardDeleted(event);

      // Then
      verify(walletHardDeleteService, times(1)).deleteByUserIds(userIds);
    }

    @Test
    @DisplayName("Service returns 0 when no wallets to delete")
    void handleUsersHardDeleted_NoWalletsToDelete_Succeeds() {
      // Given
      List<Long> userIds = List.of(1L, 2L);
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(userIds);

      when(walletHardDeleteService.deleteByUserIds(userIds)).thenReturn(0);

      // When
      eventHandler.handleUsersHardDeleted(event);

      // Then
      verify(walletHardDeleteService, times(1)).deleteByUserIds(userIds);
    }
  }

  // ========================================
  // Edge Cases
  // ========================================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCases {

    @Test
    @DisplayName("Empty user IDs list does not call service")
    void handleUsersHardDeleted_EmptyUserIds_DoesNotCallService() {
      // Given
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(Collections.emptyList());

      // When
      eventHandler.handleUsersHardDeleted(event);

      // Then
      verify(walletHardDeleteService, never()).deleteByUserIds(anyList());
    }

    @Test
    @DisplayName("Null user IDs does not call service")
    void handleUsersHardDeleted_NullUserIds_DoesNotCallService() {
      // Given
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(null);

      // When
      eventHandler.handleUsersHardDeleted(event);

      // Then
      verify(walletHardDeleteService, never()).deleteByUserIds(anyList());
    }
  }

  // ========================================
  // Error Handling Cases
  // ========================================

  @Nested
  @DisplayName("Error Handling Cases")
  class ErrorHandlingCases {

    @Test
    @DisplayName("Service exception propagates to roll back TX and trigger scheduler retry")
    void handleUsersHardDeleted_ServiceThrowsException_Propagates() {
      // Given
      List<Long> userIds = List.of(1L);
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(userIds);

      when(walletHardDeleteService.deleteByUserIds(userIds))
          .thenThrow(new RuntimeException("Database error"));

      // When & Then — exception must propagate so the enclosing TX is rolled back
      assertThatThrownBy(() -> eventHandler.handleUsersHardDeleted(event))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Database error");

      verify(walletHardDeleteService, times(1)).deleteByUserIds(userIds);
    }

    @Test
    @DisplayName("Service exception propagates regardless of exception type")
    void handleUsersHardDeleted_ExceptionPropagates() {
      // Given
      List<Long> userIds = List.of(1L, 2L, 3L);
      UsersHardDeletedEvent event = new UsersHardDeletedEvent(userIds);

      when(walletHardDeleteService.deleteByUserIds(userIds))
          .thenThrow(new IllegalStateException("Invalid state"));

      // When & Then — exception re-thrown so TX rolls back and scheduler retries
      assertThatThrownBy(() -> eventHandler.handleUsersHardDeleted(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invalid state");

      verify(walletHardDeleteService, times(1)).deleteByUserIds(userIds);
    }
  }
}
