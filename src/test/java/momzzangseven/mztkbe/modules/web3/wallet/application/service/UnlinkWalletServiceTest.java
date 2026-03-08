package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.global.error.wallet.UnauthorizedWalletAccessException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for UnlinkWalletService
 *
 * <p>Uses Mockito to isolate service logic from dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnlinkWalletService Unit Test")
class UnlinkWalletServiceTest {

  @Mock private LoadWalletPort loadWalletPort;
  @Mock private SaveWalletPort saveWalletPort;
  @Mock private RecordWalletEventPort eventPort;

  @InjectMocks private UnlinkWalletService unlinkWalletService;

  private static final Long VALID_USER_ID = 1L;
  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";

  private UserWallet validWallet;

  @BeforeEach
  void setUp() {
    validWallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, Instant.now());
  }

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("Valid wallet unlinking succeeds")
    void execute_ValidCommand_UnlinksWallet() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, VALID_WALLET_ADDRESS);

      when(loadWalletPort.findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of(validWallet));
      when(saveWalletPort.save(any(UserWallet.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // When
      unlinkWalletService.execute(command);

      // Then
      verify(loadWalletPort, times(1))
          .findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE);
      verify(saveWalletPort, times(1))
          .save(argThat(wallet -> wallet.getStatus() == WalletStatus.UNLINKED));
      verify(eventPort, times(1)).record(any(WalletEvent.class));
    }

    @Test
    @DisplayName("Unlinked wallet is saved with correct status")
    void execute_SavesUnlinkedWallet() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, VALID_WALLET_ADDRESS);

      when(loadWalletPort.findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of(validWallet));
      when(saveWalletPort.save(any(UserWallet.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // When
      unlinkWalletService.execute(command);

      // Then
      verify(saveWalletPort)
          .save(
              argThat(
                  wallet ->
                      wallet.getStatus() == WalletStatus.UNLINKED
                          && wallet.getUserId().equals(VALID_USER_ID)
                          && wallet.getWalletAddress().equals(VALID_WALLET_ADDRESS.toLowerCase())
                          && wallet.getUnlinkedAt() != null));
    }

    @Test
    @DisplayName("UNLINKED event is recorded")
    void execute_RecordsUnlinkedEvent() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, VALID_WALLET_ADDRESS);

      when(loadWalletPort.findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of(validWallet));
      when(saveWalletPort.save(any(UserWallet.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // When
      unlinkWalletService.execute(command);

      // Then
      verify(eventPort, times(1)).record(any(WalletEvent.class));
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
    void execute_NullUserId_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(null, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Zero userId throws exception")
    void execute_ZeroUserId_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(0L, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Negative userId throws exception")
    void execute_NegativeUserId_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(-1L, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Null wallet address throws exception")
    void execute_NullWalletAddress_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, null);

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Empty wallet address throws exception")
    void execute_EmptyWalletAddress_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, "");

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Blank wallet address throws exception")
    void execute_BlankWalletAddress_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, "   ");

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Invalid wallet address format throws exception")
    void execute_InvalidWalletAddressFormat_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, "invalid-address");

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid Ethereum address format");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Wallet address without 0x prefix throws exception")
    void execute_WalletAddressWithout0x_ThrowsException() {
      // Given
      UnlinkWalletCommand command =
          new UnlinkWalletCommand(VALID_USER_ID, "5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid Ethereum address format");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("Wallet address too short throws exception")
    void execute_WalletAddressTooShort_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, "0x5aAeb6053");

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid Ethereum address format");

      verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
      verify(saveWalletPort, never()).save(any());
    }
  }

  // ========================================
  // Wallet Not Found Cases
  // ========================================

  @Nested
  @DisplayName("Wallet Not Found Cases")
  class WalletNotFoundCases {

    @Test
    @DisplayName("Wallet not found throws WalletNotFoundException")
    void execute_WalletNotFound_ThrowsException() {
      // Given
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, VALID_WALLET_ADDRESS);

      when(loadWalletPort.findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of());

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(WalletNotFoundException.class);

      verify(loadWalletPort, times(1))
          .findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE);
      verify(saveWalletPort, never()).save(any());
      verify(eventPort, never()).record(any());
    }
  }

  // ========================================
  // Unauthorized Access Cases
  // ========================================

  @Nested
  @DisplayName("Unauthorized Access Cases")
  class UnauthorizedAccessCases {

    @Test
    @DisplayName("User mismatch throws UnauthorizedWalletAccessException")
    void execute_UserMismatch_ThrowsException() {
      // Given
      Long differentUserId = 999L;
      UserWallet otherUserWallet =
          UserWallet.create(differentUserId, VALID_WALLET_ADDRESS, Instant.now());
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, VALID_WALLET_ADDRESS);

      when(loadWalletPort.findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of(otherUserWallet));

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(UnauthorizedWalletAccessException.class);

      verify(saveWalletPort, never()).save(any());
      verify(eventPort, never()).record(any());
    }

    @Test
    @DisplayName("Unauthorized user cannot unlink wallet")
    void execute_UnauthorizedUser_DoesNotSaveWallet() {
      // Given
      Long unauthorizedUserId = 888L;
      UserWallet otherUserWallet =
          UserWallet.create(unauthorizedUserId, VALID_WALLET_ADDRESS, Instant.now());
      UnlinkWalletCommand command = new UnlinkWalletCommand(VALID_USER_ID, VALID_WALLET_ADDRESS);

      when(loadWalletPort.findWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(List.of(otherUserWallet));

      // When & Then
      assertThatThrownBy(() -> unlinkWalletService.execute(command))
          .isInstanceOf(UnauthorizedWalletAccessException.class);

      verify(saveWalletPort, never()).save(any());
      verify(eventPort, never()).record(any());
    }
  }
}
