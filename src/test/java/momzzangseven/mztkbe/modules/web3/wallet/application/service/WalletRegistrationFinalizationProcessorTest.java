package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.WalletRegistrationLocalConflictException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.AcquireWalletRegistrationAuthorityLockPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.DeleteWalletAndFlushPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RecordWalletEventPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletAndFlushPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletEvent;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationFinalizationProcessorTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final Long USER_ID = 1L;
  private static final String WALLET_ADDRESS = "0x" + "a".repeat(40);

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;
  @Mock private AcquireWalletRegistrationAuthorityLockPort authorityLockPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;
  @Mock private LoadWalletPort loadWalletPort;
  @Mock private SaveWalletAndFlushPort saveWalletAndFlushPort;
  @Mock private DeleteWalletAndFlushPort deleteWalletAndFlushPort;
  @Mock private RecordWalletEventPort recordWalletEventPort;

  private WalletRegistrationFinalizationProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new WalletRegistrationFinalizationProcessor(
            lockSessionPort,
            loadSessionPort,
            authorityLockPort,
            saveSessionPort,
            loadWalletPort,
            saveWalletAndFlushPort,
            deleteWalletAndFlushPort,
            recordWalletEventPort,
            CLOCK);
  }

  @Test
  void finalizeConfirmed_runsInIndependentTransaction() throws Exception {
    Transactional transactional =
        WalletRegistrationFinalizationProcessor.class
            .getMethod("finalizeConfirmed", FinalizeWalletRegistrationCommand.class)
            .getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }

  @Test
  void finalizeConfirmed_forFreshWallet_createsActiveWalletAndMarksRegistered() {
    WalletRegistrationSession session = approvalRequiredSession();
    UserWallet savedWallet =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    processor.finalizeConfirmed(command());

    ArgumentCaptor<WalletRegistrationSession> sessionCaptor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(sessionCaptor.capture());
    assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.REGISTERED);
    assertThat(sessionCaptor.getValue().getRegisteredWalletId()).isEqualTo(77L);
    verify(authorityLockPort).lock(USER_ID, WALLET_ADDRESS);
    verify(recordWalletEventPort).record(any(WalletEvent.class));
  }

  @Test
  void finalizeConfirmed_forUnlinkedWallet_deletesAndFlushesBeforeRegisteringReplacement() {
    WalletRegistrationSession session = pendingSession();
    UserWallet existing =
        UserWallet.builder()
            .id(9L)
            .userId(2L)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.UNLINKED)
            .registeredAt(CLOCK.instant())
            .build();
    UserWallet savedWallet =
        UserWallet.builder()
            .id(88L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.of(existing));
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    processor.finalizeConfirmed(command());

    verify(deleteWalletAndFlushPort).deleteByIdAndFlush(9L);
    verify(recordWalletEventPort, times(2)).record(any(WalletEvent.class));
    verify(saveWalletAndFlushPort).saveAndFlush(any(UserWallet.class));
  }

  @Test
  void finalizeConfirmed_forUserDeletedWallet_deletesAndFlushesBeforeRegisteringReplacement() {
    WalletRegistrationSession session = pendingSession();
    UserWallet existing =
        UserWallet.builder()
            .id(9L)
            .userId(2L)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.USER_DELETED)
            .registeredAt(CLOCK.instant())
            .build();
    UserWallet savedWallet =
        UserWallet.builder()
            .id(88L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.of(existing));
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    processor.finalizeConfirmed(command());

    verify(deleteWalletAndFlushPort).deleteByIdAndFlush(9L);
    verify(recordWalletEventPort, times(2)).record(any(WalletEvent.class));
  }

  @Test
  void finalizeConfirmed_whenSameActiveWalletAlreadyExists_marksRegisteredIdempotently() {
    WalletRegistrationSession session = pendingSession();
    UserWallet active =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of(active));

    processor.finalizeConfirmed(command());

    ArgumentCaptor<WalletRegistrationSession> sessionCaptor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(sessionCaptor.capture());
    assertThat(sessionCaptor.getValue().getRegisteredWalletId()).isEqualTo(77L);
    verify(saveWalletAndFlushPort, never()).saveAndFlush(any());
  }

  @Test
  void finalizeConfirmed_whenMultipleActiveWalletsIncludeMatchingWallet_throwsLocalConflict() {
    WalletRegistrationSession session = pendingSession();
    UserWallet otherActive =
        UserWallet.builder()
            .id(78L)
            .userId(USER_ID)
            .walletAddress("0x" + "b".repeat(40))
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    UserWallet matchingActive =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of(otherActive, matchingActive));

    assertThatThrownBy(() -> processor.finalizeConfirmed(command()))
        .isInstanceOf(WalletRegistrationLocalConflictException.class)
        .hasMessageContaining("active wallet");
  }

  @Test
  void finalizeConfirmed_whenMatchingWalletPrecedesOtherActiveWallet_throwsLocalConflict() {
    WalletRegistrationSession session = pendingSession();
    UserWallet matchingActive =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    UserWallet otherActive =
        UserWallet.builder()
            .id(78L)
            .userId(USER_ID)
            .walletAddress("0x" + "b".repeat(40))
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of(matchingActive, otherActive));

    assertThatThrownBy(() -> processor.finalizeConfirmed(command()))
        .isInstanceOf(WalletRegistrationLocalConflictException.class)
        .hasMessageContaining("active wallet");
  }

  @Test
  void finalizeConfirmed_whenUserHasDifferentActiveWallet_throwsLocalConflict() {
    WalletRegistrationSession session = pendingSession();
    UserWallet active =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress("0x" + "b".repeat(40))
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of(active));

    assertThatThrownBy(() -> processor.finalizeConfirmed(command()))
        .isInstanceOf(WalletRegistrationLocalConflictException.class)
        .hasMessageContaining("active wallet");
  }

  @Test
  void finalizeConfirmed_whenWalletAddressIsBlocked_throwsLocalConflict() {
    WalletRegistrationSession session = pendingSession();
    UserWallet blocked =
        UserWallet.builder()
            .id(77L)
            .userId(2L)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.BLOCKED)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.of(blocked));

    assertThatThrownBy(() -> processor.finalizeConfirmed(command()))
        .isInstanceOf(WalletRegistrationLocalConflictException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  void finalizeConfirmed_whenStaleExecutionIntent_noops() {
    WalletRegistrationSession session = pendingSession();
    givenFinalizationSession(session);

    processor.finalizeConfirmed(new FinalizeWalletRegistrationCommand(REGISTRATION_ID, "old"));

    verify(saveSessionPort, never()).save(any());
    verify(saveWalletAndFlushPort, never()).saveAndFlush(any());
  }

  @Test
  void finalizeConfirmed_whenOldReceiptTimeoutIntentAlreadyRetried_finalizesRecoveredSuccess() {
    WalletRegistrationSession retried =
        pendingSession()
            .markApprovalRetryable("RECEIPT_TIMEOUT", "timeout", NOW.plusSeconds(4))
            .attachApprovalIntentPreservingDeadline("intent-2", NOW.plusSeconds(5));
    UserWallet savedWallet =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(retried);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    WalletRegistrationFinalizationResult result = processor.finalizeConfirmed(command());

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.REGISTERED);
    assertThat(captor.getValue().getLatestExecutionIntentId()).isEqualTo(INTENT_ID);
    assertThat(captor.getValue().getLatestTransactionId()).isNull();
    assertThat(captor.getValue().getLatestTransactionHash()).isNull();
    assertThat(result.supersededExecutionIntentId()).isEqualTo("intent-2");
  }

  @Test
  void
      finalizeConfirmed_whenOldReceiptTimeoutIntentRetriedAndNewIntentFailed_finalizesOldSuccess() {
    WalletRegistrationSession retriedFailed =
        pendingSession()
            .markApprovalRetryable("RECEIPT_TIMEOUT", "timeout", NOW.plusSeconds(4))
            .attachApprovalIntentPreservingDeadline("intent-2", NOW.plusSeconds(5))
            .markApprovalFailed("FAILED_ONCHAIN", "second attempt failed", NOW.plusSeconds(6));
    UserWallet savedWallet =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(retriedFailed);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    WalletRegistrationFinalizationResult result = processor.finalizeConfirmed(command());

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.REGISTERED);
    assertThat(captor.getValue().getLatestExecutionIntentId()).isEqualTo(INTENT_ID);
    assertThat(result.supersededExecutionIntentId()).isEqualTo("intent-2");
  }

  @Test
  void finalizeConfirmed_whenSessionMissing_throwsInvalidInput() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> processor.finalizeConfirmed(command()))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("registrationId not found");

    verify(saveSessionPort, never()).save(any());
    verify(saveWalletAndFlushPort, never()).saveAndFlush(any());
  }

  @Test
  void finalizeConfirmed_whenAlreadyRegistered_noopsIdempotently() {
    WalletRegistrationSession session = pendingSession().markRegistered(77L, NOW.plusSeconds(4));
    givenFinalizationSession(session);

    processor.finalizeConfirmed(command());

    verify(saveSessionPort, never()).save(any());
    verify(saveWalletAndFlushPort, never()).saveAndFlush(any());
  }

  @Test
  void finalizeConfirmed_whenNewerSameUserOrWalletSessionExists_noops() {
    WalletRegistrationSession session = pendingSession().toBuilder().id(1L).createdAt(NOW).build();
    givenFinalizationSession(session);
    when(loadSessionPort.existsNewerByUserIdOrWalletAddress(USER_ID, WALLET_ADDRESS, 1L))
        .thenReturn(true);

    processor.finalizeConfirmed(command());

    verify(saveSessionPort, never()).save(any());
    verify(saveWalletAndFlushPort, never()).saveAndFlush(any());
    verify(loadWalletPort, never()).findWalletsByUserIdAndStatus(any(), any());
  }

  @Test
  void finalizeConfirmed_whenStatusIsNotFinalizable_noops() {
    WalletRegistrationSession session =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    givenFinalizationSession(session);

    processor.finalizeConfirmed(command());

    verify(saveSessionPort, never()).save(any());
    verify(saveWalletAndFlushPort, never()).saveAndFlush(any());
  }

  @Test
  void finalizeConfirmed_whenReceiptTimeoutRetryable_finalizesLateSuccess() {
    WalletRegistrationSession session =
        pendingSession().markApprovalRetryable("RECEIPT_TIMEOUT", "timeout", NOW.plusSeconds(4));
    UserWallet savedWallet =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    processor.finalizeConfirmed(command());

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.REGISTERED);
  }

  @Test
  void finalizeConfirmed_whenReceiptTimeoutFailed_finalizesLateSuccess() {
    WalletRegistrationSession session =
        pendingSession().markApprovalFailed("RECEIPT_TIMEOUT", "timeout", NOW.plusSeconds(4));
    UserWallet savedWallet =
        UserWallet.builder()
            .id(77L)
            .userId(USER_ID)
            .walletAddress(WALLET_ADDRESS)
            .status(WalletStatus.ACTIVE)
            .registeredAt(CLOCK.instant())
            .build();
    givenFinalizationSession(session);
    when(loadWalletPort.findWalletsByUserIdAndStatus(USER_ID, WalletStatus.ACTIVE))
        .thenReturn(List.of());
    when(loadWalletPort.findByWalletAddress(WALLET_ADDRESS)).thenReturn(Optional.empty());
    when(saveWalletAndFlushPort.saveAndFlush(any())).thenReturn(savedWallet);

    processor.finalizeConfirmed(command());

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.REGISTERED);
  }

  private static FinalizeWalletRegistrationCommand command() {
    return new FinalizeWalletRegistrationCommand(REGISTRATION_ID, INTENT_ID);
  }

  private void givenFinalizationSession(WalletRegistrationSession session) {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.of(session));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(session));
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, USER_ID, WALLET_ADDRESS, "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession pendingSession() {
    return approvalRequiredSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(2))
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
  }
}
