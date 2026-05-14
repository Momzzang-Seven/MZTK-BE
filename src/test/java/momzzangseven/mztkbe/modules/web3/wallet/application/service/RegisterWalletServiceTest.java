package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.challenge.ChallengeAlreadyUsedException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeMismatchWalletAddressException;
import momzzangseven.mztkbe.global.error.challenge.ChallengeNotFoundException;
import momzzangseven.mztkbe.global.error.signature.InvalidSignatureException;
import momzzangseven.mztkbe.global.error.wallet.WalletAlreadyExistsException;
import momzzangseven.mztkbe.global.error.wallet.WalletAlreadyLinkedException;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.global.error.wallet.WalletBlackListException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationChallengeView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationDuplicateResolution;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationNextAction;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.DuplicateWalletRegistrationSessionException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ExpireWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletApprovalAttemptUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationChallengePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeExpiredPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeUsedPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.VerifyWalletOwnershipSignaturePort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterWalletService Unit Test")
class RegisterWalletServiceTest {

  private static final Long VALID_USER_ID = 1L;
  private static final Long DIFFERENT_USER_ID = 2L;
  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
  private static final String VALID_SIGNATURE = "0x" + "a".repeat(130);
  private static final String VALID_NONCE = "550e8400-e29b-41d4-a716-446655440000";
  private static final String EIP7702_DEADLINE_TOO_CLOSE = "EIP7702_DEADLINE_TOO_CLOSE";
  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

  @Mock private LoadWalletRegistrationChallengePort loadChallengePort;
  @Mock private MarkWalletRegistrationChallengeUsedPort markChallengeUsedPort;
  @Mock private MarkWalletRegistrationChallengeExpiredPort markChallengeExpiredPort;
  @Mock private VerifyWalletOwnershipSignaturePort verifySignaturePort;
  @Mock private LoadWalletPort loadWalletPort;
  @Mock private LoadWalletApprovalCapabilityPort loadWalletApprovalCapabilityPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadWalletApprovalExecutionStatePort;
  @Mock private WalletRegistrationSessionDuplicateResolver duplicateResolver;
  @Mock private RegisterWalletApprovalAttemptUseCase approvalAttemptService;
  @Mock private ExpireWalletRegistrationSessionUseCase expireSessionUseCase;

  private RegisterWalletService registerWalletService;
  private WalletRegistrationChallengeView validChallenge;

  @BeforeEach
  void setUp() {
    validChallenge = challenge(false, false);
    registerWalletService =
        new RegisterWalletService(
            loadChallengePort,
            markChallengeUsedPort,
            markChallengeExpiredPort,
            verifySignaturePort,
            loadWalletPort,
            loadWalletApprovalCapabilityPort,
            loadWalletApprovalExecutionStatePort,
            duplicateResolver,
            approvalAttemptService,
            expireSessionUseCase,
            FIXED_CLOCK);
  }

  @Test
  @DisplayName("valid ownership creates pending approval session, not ACTIVE wallet")
  void execute_NewWallet_ReturnsPendingApproval() {
    RegisterWalletCommand command = validCommand();
    RegisterWalletResult pendingResult = RegisterWalletResult.pending(pendingSession(), web3View());
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(approvalAttemptService.createPendingApproval(command)).thenReturn(pendingResult);

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(result.walletId()).isNull();
    assertThat(result.web3()).isNotNull();
    verify(approvalAttemptService).createPendingApproval(command);
  }

  @Test
  @DisplayName("challenge is not consumed before approval attempt succeeds")
  void execute_ApprovalUnavailable_DoesNotUseChallenge() {
    RegisterWalletCommand command = validCommand();
    when(loadChallengePort.load(VALID_NONCE)).thenReturn(Optional.of(validChallenge));
    when(verifySignaturePort.verify(
            any(), eq(VALID_NONCE), eq(VALID_SIGNATURE), eq(VALID_WALLET_ADDRESS)))
        .thenReturn(true);
    when(loadWalletApprovalCapabilityPort.load())
        .thenReturn(WalletApprovalCapability.unavailable("disabled"));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletApprovalUnavailableException.class);

    verify(markChallengeUsedPort, never()).markUsed(any());
    verify(markChallengeExpiredPort, never()).markExpired(any());
    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("sponsor cap race during approval attempt is mapped to wallet approval unavailable")
  void execute_ApprovalAttemptSponsorCapExceeded_ThrowsWalletApprovalUnavailable() {
    RegisterWalletCommand command = validCommand();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(approvalAttemptService.createPendingApproval(command))
        .thenThrow(new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletApprovalUnavailableException.class)
        .hasMessageContaining("Sponsor daily limit exceeded");

    verify(markChallengeUsedPort, never()).markUsed(any());
  }

  @Test
  @DisplayName("same user and wallet non-terminal session is reused")
  void execute_DuplicateSameUserWallet_ReusesSession() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession existing = pendingSession();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(existing));
    when(loadWalletApprovalExecutionStatePort.loadByExecutionIntentId(VALID_USER_ID, "intent-1"))
        .thenReturn(Optional.of(executionState()));

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.registrationId()).isEqualTo("registration-1");
    assertThat(result.web3()).isNotNull();
    verify(markChallengeUsedPort).markUsed(VALID_NONCE);
    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("same user and wallet deadline-too-close session is reused with unavailable reason")
  void execute_DuplicateSameUserWalletDeadlineTooClose_ReusesSessionWithReason() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession existing = pendingSession();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(existing));
    when(loadWalletApprovalExecutionStatePort.loadByExecutionIntentId(VALID_USER_ID, "intent-1"))
        .thenReturn(Optional.of(deadlineTooCloseExecutionState()));

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().signRequest()).isNull();
    assertThat(result.web3().signRequestUnavailableReason()).isEqualTo(EIP7702_DEADLINE_TOO_CLOSE);
    verify(markChallengeUsedPort).markUsed(VALID_NONCE);
    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("same user and wallet retryable session is reused with retry nextAction")
  void execute_DuplicateRetryableSession_ReturnsRetryAction() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession existing = retryableSession();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(existing));

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.registrationId()).isEqualTo("registration-1");
    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_RETRYABLE);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.RETRY_APPROVAL);
    assertThat(result.web3()).isNull();
    verify(markChallengeUsedPort).markUsed(VALID_NONCE);
    verifyNoInteractions(approvalAttemptService);
    verify(loadWalletApprovalExecutionStatePort, never()).loadByExecutionIntentId(any(), any());
  }

  @Test
  @DisplayName("same user and wallet expired duplicate is expired before creating new approval")
  void execute_DuplicateSameUserWalletExpired_ExpiresAndCreatesNewSession() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession expired = expiredPendingSession();
    RegisterWalletResult pendingResult = RegisterWalletResult.pending(pendingSession(), web3View());
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(expired))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(expireSessionUseCase.execute(
            new ExpireWalletRegistrationSessionCommand(expired.getPublicId())))
        .thenReturn(true);
    when(approvalAttemptService.createPendingApproval(command)).thenReturn(pendingResult);

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(result.web3()).isNotNull();
    verify(expireSessionUseCase)
        .execute(new ExpireWalletRegistrationSessionCommand(expired.getPublicId()));
    verify(approvalAttemptService).createPendingApproval(command);
    verify(markChallengeUsedPort, never()).markUsed(VALID_NONCE);
  }

  @Test
  @DisplayName(
      "expired duplicate found after create race is expired and the approval attempt retries")
  void execute_DuplicateRaceExpired_ExpiresAndRetriesApprovalAttempt() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession expired = expiredPendingSession();
    RegisterWalletResult pendingResult = RegisterWalletResult.pending(pendingSession(), web3View());
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew())
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(approvalAttemptService.createPendingApproval(command))
        .thenThrow(
            new DuplicateWalletRegistrationSessionException(
                VALID_USER_ID, VALID_WALLET_ADDRESS, new RuntimeException("duplicate")))
        .thenReturn(pendingResult);
    when(duplicateResolver.resolveAfterCreateRace(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(expired));
    when(expireSessionUseCase.execute(
            new ExpireWalletRegistrationSessionCommand(expired.getPublicId())))
        .thenReturn(true);

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.registrationId()).isEqualTo("registration-1");
    verify(expireSessionUseCase)
        .execute(new ExpireWalletRegistrationSessionCommand(expired.getPublicId()));
    verify(approvalAttemptService, org.mockito.Mockito.times(2)).createPendingApproval(command);
  }

  @Test
  @DisplayName("same user and wallet submitted session is reused without web3 sign request")
  void execute_DuplicateSubmittedSession_ReusesWithoutWeb3() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession existing = signedSession();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(existing));

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.registrationId()).isEqualTo("registration-1");
    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_SIGNED);
    assertThat(result.web3()).isNull();
    verifyNoInteractions(approvalAttemptService);
    verify(loadWalletApprovalExecutionStatePort, never()).loadByExecutionIntentId(any(), any());
  }

  @Test
  @DisplayName("partial unique race is resolved after rollback and reused for same user/wallet")
  void execute_DuplicateRace_ReusesWinningSession() {
    RegisterWalletCommand command = validCommand();
    WalletRegistrationSession existing = pendingSession();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(approvalAttemptService.createPendingApproval(command))
        .thenThrow(
            new DuplicateWalletRegistrationSessionException(
                VALID_USER_ID, VALID_WALLET_ADDRESS, new RuntimeException("duplicate")));
    when(duplicateResolver.resolveAfterCreateRace(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.reuse(existing));
    when(loadWalletApprovalExecutionStatePort.loadByExecutionIntentId(VALID_USER_ID, "intent-1"))
        .thenReturn(Optional.of(executionState()));

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.registrationId()).isEqualTo("registration-1");
    verify(duplicateResolver).resolveAfterCreateRace(VALID_USER_ID, VALID_WALLET_ADDRESS);
  }

  @Test
  @DisplayName("same user with different pending wallet is rejected")
  void execute_DuplicateSameUserDifferentWallet_ThrowsAlreadyLinked() {
    RegisterWalletCommand command = validCommand();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.userConflict(pendingSession()));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletAlreadyLinkedException.class);

    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("same wallet with different pending user is rejected")
  void execute_DuplicateSameWalletDifferentUser_ThrowsAlreadyExists() {
    RegisterWalletCommand command = validCommand();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.walletConflict(pendingSession()));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletAlreadyExistsException.class);

    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("UNLINKED wallet can create pending session without immediate re-registration")
  void execute_UnlinkedWallet_AllowsPendingSession() {
    RegisterWalletCommand command = validCommand();
    UserWallet unlinked =
        UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now()).unlink();
    RegisterWalletResult pendingResult = RegisterWalletResult.pending(pendingSession(), web3View());
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
        .thenReturn(Optional.of(unlinked));
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(approvalAttemptService.createPendingApproval(command)).thenReturn(pendingResult);

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.walletId()).isNull();
    verify(approvalAttemptService).createPendingApproval(command);
  }

  @Test
  @DisplayName("USER_DELETED wallet can create pending session without immediate re-registration")
  void execute_UserDeletedWallet_AllowsPendingSession() {
    RegisterWalletCommand command = validCommand();
    UserWallet userDeleted =
        UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now())
            .markAsUserDeleted();
    RegisterWalletResult pendingResult = RegisterWalletResult.pending(pendingSession(), web3View());
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS))
        .thenReturn(Optional.of(userDeleted));
    when(duplicateResolver.resolveCurrent(VALID_USER_ID, VALID_WALLET_ADDRESS))
        .thenReturn(WalletRegistrationDuplicateResolution.createNew());
    when(approvalAttemptService.createPendingApproval(command)).thenReturn(pendingResult);

    RegisterWalletResult result = registerWalletService.execute(command);

    assertThat(result.walletId()).isNull();
    verify(approvalAttemptService).createPendingApproval(command);
  }

  @Test
  @DisplayName("active wallet owned by another user is rejected")
  void execute_ActiveWallet_ThrowsAlreadyExists() {
    RegisterWalletCommand command = validCommand();
    UserWallet active = UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now());
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.of(active));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletAlreadyExistsException.class);

    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("blocked wallet is rejected")
  void execute_BlockedWallet_ThrowsException() {
    RegisterWalletCommand command = validCommand();
    UserWallet blocked =
        UserWallet.create(DIFFERENT_USER_ID, VALID_WALLET_ADDRESS, Instant.now()).block();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(0);
    when(loadWalletPort.findByWalletAddress(VALID_WALLET_ADDRESS)).thenReturn(Optional.of(blocked));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletBlackListException.class);

    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("user already has active wallet is rejected")
  void execute_UserAlreadyHasWallet_ThrowsException() {
    RegisterWalletCommand command = validCommand();
    givenValidOwnership(command);
    when(loadWalletPort.countWalletsByUserIdAndStatus(VALID_USER_ID, WalletStatus.ACTIVE))
        .thenReturn(1);

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(WalletAlreadyLinkedException.class);

    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("invalid signature is rejected before local writes")
  void execute_InvalidSignature_ThrowsException() {
    RegisterWalletCommand command = validCommand();
    when(loadChallengePort.load(VALID_NONCE)).thenReturn(Optional.of(validChallenge));
    when(verifySignaturePort.verify(
            any(), eq(VALID_NONCE), eq(VALID_SIGNATURE), eq(VALID_WALLET_ADDRESS)))
        .thenReturn(false);

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(InvalidSignatureException.class);

    verify(markChallengeUsedPort, never()).markUsed(any());
    verifyNoInteractions(approvalAttemptService);
  }

  @Test
  @DisplayName("challenge not found is rejected")
  void execute_ChallengeNotFound_ThrowsException() {
    RegisterWalletCommand command = validCommand();
    when(loadChallengePort.load(VALID_NONCE)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(ChallengeNotFoundException.class);
  }

  @Test
  @DisplayName("used challenge is rejected")
  void execute_UsedChallenge_ThrowsException() {
    RegisterWalletCommand command = validCommand();
    when(loadChallengePort.load(VALID_NONCE)).thenReturn(Optional.of(challenge(true, false)));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(ChallengeAlreadyUsedException.class);
  }

  @Test
  @DisplayName("address mismatch is rejected")
  void execute_AddressMismatch_ThrowsException() {
    RegisterWalletCommand command =
        new RegisterWalletCommand(
            VALID_USER_ID, "0x" + "b".repeat(40), VALID_SIGNATURE, VALID_NONCE);
    when(loadChallengePort.load(VALID_NONCE)).thenReturn(Optional.of(validChallenge));

    assertThatThrownBy(() -> registerWalletService.execute(command))
        .isInstanceOf(ChallengeMismatchWalletAddressException.class);
  }

  private void givenValidOwnership(RegisterWalletCommand command) {
    when(loadChallengePort.load(command.nonce())).thenReturn(Optional.of(validChallenge));
    when(verifySignaturePort.verify(
            any(), eq(command.nonce()), eq(command.signature()), eq(command.walletAddress())))
        .thenReturn(true);
    when(loadWalletApprovalCapabilityPort.load()).thenReturn(WalletApprovalCapability.enabled());
  }

  private static RegisterWalletCommand validCommand() {
    return new RegisterWalletCommand(
        VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_SIGNATURE, VALID_NONCE);
  }

  private static WalletRegistrationSession pendingSession() {
    return WalletRegistrationSession.create(
            "registration-1",
            VALID_USER_ID,
            VALID_WALLET_ADDRESS,
            VALID_NONCE,
            NOW.plusMinutes(30),
            NOW)
        .attachApprovalIntent("intent-1", NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession expiredPendingSession() {
    return WalletRegistrationSession.create(
            "registration-expired",
            VALID_USER_ID,
            VALID_WALLET_ADDRESS,
            VALID_NONCE,
            NOW.minusSeconds(1),
            NOW.minusMinutes(31))
        .attachApprovalIntent("intent-expired", NOW.minusSeconds(1), NOW.minusMinutes(30));
  }

  private static WalletRegistrationSession signedSession() {
    return pendingSession()
        .markApprovalSigned("intent-1", 11L, "0x" + "c".repeat(64), "SIGNED", NOW.plusSeconds(2));
  }

  private static WalletRegistrationSession retryableSession() {
    return pendingSession()
        .markApprovalRetryable("FAILED_ONCHAIN", "approval failed", NOW.plusSeconds(2));
  }

  private static WalletApprovalExecutionWriteView web3View() {
    return WalletApprovalExecutionWriteView.from(executionState());
  }

  private static WalletApprovalExecutionStateView executionState() {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        "registration-1",
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        "intent-1",
        "AWAITING_SIGNATURE",
        NOW.plusMinutes(5),
        1L,
        "EIP7702",
        2,
        WalletApprovalSignRequestBundle.forEip7702(
            new WalletApprovalSignRequestBundle.AuthorizationSignRequest(
                10L, "0x" + "d".repeat(40), 7L, "0x" + "1".repeat(64)),
            new WalletApprovalSignRequestBundle.SubmitSignRequest("0x" + "2".repeat(64), 123L)),
        null,
        null,
        null,
        null);
  }

  private static WalletApprovalExecutionStateView deadlineTooCloseExecutionState() {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        "registration-1",
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        "intent-1",
        "AWAITING_SIGNATURE",
        NOW.plusMinutes(5),
        1L,
        "EIP7702",
        2,
        null,
        EIP7702_DEADLINE_TOO_CLOSE,
        null,
        null,
        null);
  }

  private static WalletRegistrationChallengeView challenge(boolean used, boolean expired) {
    return new WalletRegistrationChallengeView(
        VALID_USER_ID, VALID_WALLET_ADDRESS, VALID_NONCE, "message", used, expired);
  }
}
