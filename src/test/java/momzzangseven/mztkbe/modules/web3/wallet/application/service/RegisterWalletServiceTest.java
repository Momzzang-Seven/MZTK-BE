package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.challenge.ChallengeAlreadyUsedException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeMismatchWalletAddressException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeNotFoundException;
import momzzangseven.mztkbe.global.error.signature.InvalidSignatureException;
import momzzangseven.mztkbe.global.error.wallet.UnauthorizedWalletAccessException;
import momzzangseven.mztkbe.global.error.wallet.WalletAlreadyExistsException;
import momzzangseven.mztkbe.global.error.wallet.WalletAlreadyLinkedException;
import momzzangseven.mztkbe.global.error.wallet.WalletBlackListException;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.SaveChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengeStatus;
import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;
import momzzangseven.mztkbe.modules.web3.signature.application.port.out.VerifySignaturePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletPort;
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
 * Unit tests for RegisterWalletService
 *
 * <p>Uses Mockito to isolate service logic from dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterWalletService Unit Test")
class RegisterWalletServiceTest {

  @Mock private LoadChallengePort loadChallengePort;
  @Mock private SaveChallengePort saveChallengePort;
  @Mock private VerifySignaturePort verifySignaturePort;
  @Mock private LoadWalletPort loadWalletPort;
  @Mock private SaveWalletPort saveWalletPort;
  @Mock private DeleteWalletPort deleteWalletPort;
  @Mock private RecordWalletEventPort eventPort;

  @InjectMocks private RegisterWalletService registerWalletService;

  private static final Long VALID_USER_ID = 1L;
  private static final Long DIFFERENT_USER_ID = 2L;
  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
  private static final String VALID_SIGNATURE = "0x" + "a".repeat(130);
  private static final String VALID_NONCE = "550e8400-e29b-41d4-a716-446655440000";

  private Challenge validChallenge;
  private UserWallet validWallet;

  @BeforeEach
  void setUp() {
    ChallengeConfig config = new ChallengeConfig(300, "test.com", "https://test.com", "1", "1");
    validChallenge =
        Challenge.create(
            VALID_USER_ID, ChallengePurpose.WALLET_REGISTRATION, VALID_WALLET_ADDRESS, config);

    validWallet = UserWallet.create(VALID_USER_ID, VALID_WALLET_ADDRESS, Instant.now());
  }

  // ========================================
  // New Registration Success Cases
  // ========================================

  @Nested
  @DisplayName("New Registration Success Cases")
  class NewRegistrationSuccessCases {

    @Test
    @DisplayName("Valid new wallet registration succeeds")
    void execute_NewWallet_SuccessfullyRegisters() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(
              any(), eq(VALID_NONCE), eq(VALID_SIGNATURE), eq(VALID_WALLET_ADDRESS)))
          .thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
      when(saveChallengePort.save(any(Challenge.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(saveWalletPort.save(any(UserWallet.class))).thenReturn(validWallet);

      // When
      RegisterWalletResult result = registerWalletService.execute(command);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.walletId()).isEqualTo(validWallet.getId());
      assertThat(result.walletAddress()).isEqualTo(validWallet.getWalletAddress());

      verify(saveWalletPort, times(1)).save(any(UserWallet.class));
      verify(eventPort, times(1)).record(any(WalletEvent.class));
      verify(deleteWalletPort, never()).deleteById(any());
    }

    @Test
    @DisplayName("Challenge is marked as USED after successful registration")
    void execute_MarksChallengeAsUsed() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(
              any(), eq(VALID_NONCE), eq(VALID_SIGNATURE), eq(VALID_WALLET_ADDRESS)))
          .thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
      when(saveChallengePort.save(any(Challenge.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(saveWalletPort.save(any(UserWallet.class))).thenReturn(validWallet);

      // When
      registerWalletService.execute(command);

      // Then
      verify(saveChallengePort)
          .save(argThat(challenge -> challenge.getStatus() == ChallengeStatus.USED));
    }

    @Test
    @DisplayName("REGISTERED event is recorded for new wallet")
    void execute_NewWallet_RecordsRegisteredEvent() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
      when(saveChallengePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(saveWalletPort.save(any(UserWallet.class))).thenReturn(validWallet);

      // When
      registerWalletService.execute(command);

      // Then
      verify(eventPort, times(1)).record(any(WalletEvent.class));
    }
  }

  // ========================================
  // Re-Registration Success Cases (UNLINKED)
  // ========================================

  @Nested
  @DisplayName("Re-Registration Success Cases (UNLINKED)")
  class ReRegistrationUnlinkedCases {

    @Test
    @DisplayName("Re-registering UNLINKED wallet succeeds")
    void execute_UnlinkedWallet_SuccessfullyReRegisters() {
      // Given
      UserWallet unlinkedWallet =
          UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now()).unlink();

      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
          .thenReturn(Optional.of(unlinkedWallet));
      when(saveChallengePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(saveWalletPort.save(any(UserWallet.class))).thenReturn(validWallet);

      // When
      RegisterWalletResult result = registerWalletService.execute(command);

      // Then
      assertThat(result).isNotNull();
      verify(deleteWalletPort, times(1)).deleteById(unlinkedWallet.getId());
      verify(saveWalletPort, times(1)).save(any(UserWallet.class));
      verify(eventPort, times(2)).record(any(WalletEvent.class)); // HARD_DELETED + REGISTERED
    }

    @Test
    @DisplayName("Re-registration records HARD_DELETED and REGISTERED events")
    void execute_UnlinkedWallet_RecordsTwoEvents() {
      // Given
      UserWallet unlinkedWallet =
          UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now()).unlink();

      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
          .thenReturn(Optional.of(unlinkedWallet));
      when(saveChallengePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(saveWalletPort.save(any(UserWallet.class))).thenReturn(validWallet);

      // When
      registerWalletService.execute(command);

      // Then
      verify(eventPort, times(2)).record(any(WalletEvent.class));
    }
  }

  // ========================================
  // Re-Registration Success Cases (USER_DELETED)
  // ========================================

  @Nested
  @DisplayName("Re-Registration Success Cases (USER_DELETED)")
  class ReRegistrationUserDeletedCases {

    @Test
    @DisplayName("Re-registering USER_DELETED wallet succeeds")
    void execute_UserDeletedWallet_SuccessfullyReRegisters() {
      // Given
      UserWallet userDeletedWallet =
          UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now())
              .markAsUserDeleted();

      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
          .thenReturn(Optional.of(userDeletedWallet));
      when(saveChallengePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(saveWalletPort.save(any(UserWallet.class))).thenReturn(validWallet);

      // When
      RegisterWalletResult result = registerWalletService.execute(command);

      // Then
      assertThat(result).isNotNull();
      verify(deleteWalletPort, times(1)).deleteById(userDeletedWallet.getId());
      verify(saveWalletPort, times(1)).save(any(UserWallet.class));
      verify(eventPort, times(2)).record(any(WalletEvent.class));
    }
  }

  // ========================================
  // Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Validation Failure Cases")
  class ValidationFailureCases {

    @Test
    @DisplayName("Invalid command throws exception")
    void execute_InvalidCommand_ThrowsException() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(null, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(IllegalArgumentException.class);

      verify(loadChallengePort, never()).findByNonceAndPurpose(any(), any());
    }
  }

  // ========================================
  // Challenge Not Found
  // ========================================

  @Nested
  @DisplayName("Challenge Not Found Cases")
  class ChallengeNotFoundCases {

    @Test
    @DisplayName("Challenge not found throws ChallengeNotFoundException")
    void execute_ChallengeNotFound_ThrowsException() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(ChallengeNotFoundException.class);

      verify(verifySignaturePort, never()).verify(any(), any(), any(), any());
    }
  }

  // ========================================
  // Challenge Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Challenge Validation Failure Cases")
  class ChallengeValidationCases {

    @Test
    @DisplayName("Already used challenge throws ChallengeAlreadyUsedException")
    void execute_UsedChallenge_ThrowsException() {
      // Given
      Challenge usedChallenge = validChallenge.markAsUsed();
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.of(usedChallenge));

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(ChallengeAlreadyUsedException.class);

      verify(verifySignaturePort, never()).verify(any(), any(), any(), any());
    }

    @Test
    @DisplayName("User mismatch throws UnauthorizedWalletAccessException")
    void execute_UserMismatch_ThrowsException() {
      // Given
      Long differentUserId = 999L;
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              differentUserId, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.of(validChallenge));

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(UnauthorizedWalletAccessException.class);

      verify(verifySignaturePort, never()).verify(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Address mismatch throws ChallengeMismatchWalletAddressException")
    void execute_AddressMismatch_ThrowsException() {
      // Given
      String differentAddress = "0x" + "b".repeat(40);
      RegisterWalletCommand command =
          new RegisterWalletCommand(VALID_USER_ID, differentAddress, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.of(validChallenge));

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(ChallengeMismatchWalletAddressException.class);

      verify(verifySignaturePort, never()).verify(any(), any(), any(), any());
    }
  }

  // ========================================
  // Signature Verification Failure
  // ========================================

  @Nested
  @DisplayName("Signature Verification Failure Cases")
  class SignatureVerificationCases {

    @Test
    @DisplayName("Invalid signature throws InvalidSignatureException")
    void execute_InvalidSignature_ThrowsException() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(
              VALID_NONCE, ChallengePurpose.WALLET_REGISTRATION))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(
              any(), eq(VALID_NONCE), eq(VALID_SIGNATURE), eq(VALID_WALLET_ADDRESS)))
          .thenReturn(false);

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(InvalidSignatureException.class);

      verify(loadWalletPort, never()).findByWalletAddress(any());
    }
  }

  // ========================================
  // Wallet Status Checks
  // ========================================

  @Nested
  @DisplayName("Wallet Status Check Cases")
  class WalletStatusCases {

    @Test
    @DisplayName("BLOCKED wallet throws WalletBlackListException")
    void execute_BlockedWallet_ThrowsException() {
      // Given
      UserWallet blockedWallet =
          UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now()).block();

      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
          .thenReturn(Optional.of(blockedWallet));

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(WalletBlackListException.class);

      verify(deleteWalletPort, never()).deleteById(any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("ACTIVE wallet throws WalletAlreadyExistsException")
    void execute_ActiveWallet_ThrowsException() {
      // Given
      UserWallet activeWallet =
          UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now());

      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(0);
      when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
          .thenReturn(Optional.of(activeWallet));

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(WalletAlreadyExistsException.class);

      verify(deleteWalletPort, never()).deleteById(any());
      verify(saveWalletPort, never()).save(any());
    }

    @Test
    @DisplayName("User already has ACTIVE wallet throws exception")
    void execute_UserAlreadyHasWallet_ThrowsException() {
      // Given
      RegisterWalletCommand command =
          new RegisterWalletCommand(
              VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);

      when(loadChallengePort.findByNonceAndPurpose(any(), any()))
          .thenReturn(Optional.of(validChallenge));
      when(verifySignaturePort.verify(any(), any(), any(), any())).thenReturn(true);
      when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
          .thenReturn(1);

      // When & Then
      assertThatThrownBy(() -> registerWalletService.execute(command))
          .isInstanceOf(WalletAlreadyLinkedException.class);

      verify(saveChallengePort, never()).save(any());
      verify(saveWalletPort, never()).save(any());
    }
  }
}
