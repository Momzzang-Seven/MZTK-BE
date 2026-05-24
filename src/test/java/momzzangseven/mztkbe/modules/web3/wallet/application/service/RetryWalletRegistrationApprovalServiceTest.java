package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalTtlPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationNextAction;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalTtlPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RunWalletRegistrationRetryTransactionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionActionType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryWalletRegistrationApprovalServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final String RETRY_INTENT_ID = "intent-2";
  private static final String EIP7702_DEADLINE_TOO_CLOSE = "EIP7702_DEADLINE_TOO_CLOSE";
  private static final Long USER_ID = 1L;

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  @Mock private LoadWalletApprovalCapabilityPort loadWalletApprovalCapabilityPort;
  @Mock private LoadWalletApprovalTtlPolicyPort loadWalletApprovalTtlPolicyPort;
  @Mock private BuildWalletApprovalExecutionDraftPort buildDraftPort;
  @Mock private SubmitWalletApprovalExecutionDraftPort submitDraftPort;
  @Mock private CancelWalletApprovalExecutionPort cancelExecutionPort;

  private RecordingRetryTransactionPort transactionPort;
  private RetryWalletRegistrationApprovalService service;

  @BeforeEach
  void setUp() {
    transactionPort = new RecordingRetryTransactionPort();
    service =
        new RetryWalletRegistrationApprovalService(
            lockSessionPort,
            saveSessionPort,
            loadExecutionStatePort,
            loadWalletApprovalCapabilityPort,
            loadWalletApprovalTtlPolicyPort,
            buildDraftPort,
            submitDraftPort,
            cancelExecutionPort,
            transactionPort,
            CLOCK);
  }

  @Test
  void execute_whenRetryable_createsNewApprovalIntentAndAttachesIt() {
    WalletRegistrationSession retryable =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any()))
        .thenAnswer(
            invocation -> {
              assertThat(transactionPort.active()).isFalse();
              return intentResult(RETRY_INTENT_ID);
            });
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    ArgumentCaptor<WalletApprovalExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(WalletApprovalExecutionRequest.class);
    verify(buildDraftPort).build(requestCaptor.capture());
    assertThat(requestCaptor.getValue().expiresAt()).isEqualTo(NOW.plusMinutes(30));
    assertThat(requestCaptor.getValue().retryAttemptNo()).isEqualTo(1);
    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getLatestExecutionIntentId()).isEqualTo(RETRY_INTENT_ID);
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(captor.getValue().getApprovalExpiresAt()).isEqualTo(NOW.plusMinutes(30));
    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().executionIntent().id()).isEqualTo(RETRY_INTENT_ID);
  }

  @Test
  void execute_whenLegacyReceiptTimeoutRetryableBackfillsOldIntentBeforeNewIntent() {
    WalletRegistrationSession legacyRetryable =
        approvalRequiredSession()
            .markApprovalRetryable(
                WalletRegistrationReceiptTimeout.ERROR_CODE,
                WalletRegistrationReceiptTimeout.ERROR_REASON,
                NOW.plusSeconds(2))
            .toBuilder()
            .receiptTimeoutExecutionIntentIds(null)
            .build();
    WalletRegistrationSession backfilled =
        legacyRetryable.backfillReceiptTimeoutExecutionIntent(NOW);
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(legacyRetryable), Optional.of(backfilled));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).hasReceiptTimeoutExecutionIntent(INTENT_ID)).isTrue();
    assertThat(captor.getAllValues().get(1).getLatestExecutionIntentId())
        .isEqualTo(RETRY_INTENT_ID);
    assertThat(result.latestExecutionIntentId()).isEqualTo(RETRY_INTENT_ID);
  }

  @Test
  void execute_whenApprovalRequiredStillSignable_reusesCurrentSignRequest() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(
            Optional.of(state("AWAITING_SIGNATURE", null, signRequest(), NOW.plusMinutes(5))));
    givenMinimumRemainingTtl(30L);

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().executionIntent().id()).isEqualTo(INTENT_ID);
    verify(buildDraftPort, never()).build(any());
    verify(submitDraftPort, never()).submit(any());
    verify(saveSessionPort, never()).save(any());
  }

  @Test
  void execute_whenApprovalRequiredDeadlineTooClose_createsNewApprovalIntent() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(
            Optional.of(
                state(
                    "AWAITING_SIGNATURE",
                    null,
                    null,
                    NOW.plusMinutes(5),
                    EIP7702_DEADLINE_TOO_CLOSE)));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.SIGN_APPROVAL);
    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().executionIntent().id()).isEqualTo(RETRY_INTENT_ID);
    verify(submitDraftPort).submit(any());
  }

  @Test
  void execute_whenApprovalCapabilityUnavailable_rejectsBeforeCreatingDraft() {
    WalletRegistrationSession retryable =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    when(loadWalletApprovalCapabilityPort.load())
        .thenReturn(WalletApprovalCapability.unavailable("sponsor disabled"));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(WalletApprovalUnavailableException.class)
        .hasMessageContaining("sponsor disabled");

    verify(buildDraftPort, never()).build(any());
    verify(submitDraftPort, never()).submit(any());
  }

  @Test
  void execute_whenCappedDeadlineViolatesMinimumTtl_rejectsBeforeSubmit() {
    WalletRegistrationSession retryable =
        nearExpiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.minusSeconds(5));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft(NOW.plusSeconds(20)));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("too close");

    verify(submitDraftPort, never()).submit(any());
    verify(cancelExecutionPort, never()).cancelIfSignable(any(), any(), any());
  }

  @Test
  void execute_whenSponsorCapRaceOccursDuringSubmit_mapsToApprovalUnavailable() {
    WalletRegistrationSession retryable =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any()))
        .thenThrow(new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(WalletApprovalUnavailableException.class)
        .hasMessageContaining("Sponsor daily limit exceeded");

    verify(cancelExecutionPort, never()).cancelIfSignable(any(), any(), any());
  }

  @Test
  void execute_whenSessionChangesBeforeAttach_cancelsNewIntent() {
    WalletRegistrationSession retryable =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    WalletRegistrationSession changed =
        retryable.attachApprovalIntentPreservingDeadline("intent-other", NOW.plusSeconds(3));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable), Optional.of(changed));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("changed before attach");

    verify(cancelExecutionPort)
        .cancelIfSignable(
            RETRY_INTENT_ID, "APPROVAL_RETRY_ATTACH_FAILED", "approval retry attach abandoned");
  }

  @Test
  void execute_whenExistingIntentReuseLosesAttachRace_doesNotCancelSharedIntent() {
    WalletRegistrationSession retryable =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    WalletRegistrationSession changed =
        retryable.attachApprovalIntentPreservingDeadline("intent-other", NOW.plusSeconds(3));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable), Optional.of(changed));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID, true));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("changed before attach");

    verify(cancelExecutionPort, never()).cancelIfSignable(any(), any(), any());
  }

  @Test
  void execute_whenWrongUser_rejectsBeforeExecutionStateRead() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    assertThatThrownBy(() -> service.execute(command(2L)))
        .isInstanceOf(WalletNotFoundException.class);

    verify(loadExecutionStatePort, never()).loadByExecutionIntentId(any(), any());
  }

  @Test
  void execute_whenSessionExpired_rejectsRetry() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSession()));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("expired");

    verify(buildDraftPort, never()).build(any());
  }

  @Test
  void execute_whenPendingOnchain_rejectsSecondApprovalIntent() {
    WalletRegistrationSession pending =
        approvalRequiredSession()
            .markApprovalSigned(INTENT_ID, 10L, "0x" + "c".repeat(64), "SIGNED", NOW.plusSeconds(2))
            .markApprovalPendingOnchain(
                INTENT_ID, 10L, "0x" + "c".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class);

    verify(buildDraftPort, never()).build(any());
  }

  @Test
  void
      execute_whenPendingOnchainTransactionUnconfirmedAndTtlValid_marksRetryableThenCreatesNewIntent() {
    WalletRegistrationSession pending = pendingOnchainSession();
    WalletRegistrationSession retryable =
        pending.markApprovalRetryable(
            WalletRegistrationReceiptTimeout.ERROR_CODE,
            WalletRegistrationReceiptTimeout.ERROR_REASON,
            NOW.plusSeconds(4));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(pending), Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "UNCONFIRMED", null, NOW.plusMinutes(5))));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    ArgumentCaptor<WalletApprovalExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(WalletApprovalExecutionRequest.class);
    verify(buildDraftPort).build(requestCaptor.capture());
    assertThat(requestCaptor.getValue().retryAttemptNo()).isEqualTo(1);
    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).getStatus())
        .isEqualTo(WalletRegistrationStatus.APPROVAL_RETRYABLE);
    assertThat(captor.getAllValues().get(0).getLastErrorCode())
        .isEqualTo(WalletRegistrationReceiptTimeout.ERROR_CODE);
    assertThat(captor.getAllValues().get(1).getLatestExecutionIntentId())
        .isEqualTo(RETRY_INTENT_ID);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.SIGN_APPROVAL);
  }

  @Test
  void execute_whenReceiptTimeoutRetryReusesPreviousIntent_rejectsAttach() {
    WalletRegistrationSession pending = pendingOnchainSession();
    WalletRegistrationSession retryable =
        pending.markApprovalRetryable(
            WalletRegistrationReceiptTimeout.ERROR_CODE,
            WalletRegistrationReceiptTimeout.ERROR_REASON,
            NOW.plusSeconds(4));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(pending), Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "UNCONFIRMED", null, NOW.plusMinutes(5))));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(INTENT_ID, true));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reused previous intent");

    verify(saveSessionPort, org.mockito.Mockito.times(1)).save(any());
    verify(cancelExecutionPort, never()).cancelIfSignable(any(), any(), any());
  }

  @Test
  void execute_whenReceiptTimeoutRetryFindsOrphanRetryIntent_attachesExistingRetryIntent() {
    WalletRegistrationSession pending = pendingOnchainSession();
    WalletRegistrationSession retryable =
        pending.markApprovalRetryable(
            WalletRegistrationReceiptTimeout.ERROR_CODE,
            WalletRegistrationReceiptTimeout.ERROR_REASON,
            NOW.plusSeconds(4));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(pending), Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "UNCONFIRMED", null, NOW.plusMinutes(5))));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID, true));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort, org.mockito.Mockito.times(2)).save(captor.capture());
    WalletRegistrationSession attached = captor.getAllValues().get(1);
    assertThat(attached.getLatestExecutionIntentId()).isEqualTo(RETRY_INTENT_ID);
    assertThat(result.latestExecutionIntentId()).isEqualTo(RETRY_INTENT_ID);
    verify(cancelExecutionPort, never()).cancelIfSignable(any(), any(), any());
  }

  @Test
  void execute_whenRetryIntentReuseIsAlreadyConfirmed_rejectsAttach() {
    WalletRegistrationSession retryable =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "expired", NOW.plusSeconds(2));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(retryable), Optional.of(retryable));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(state("EXPIRED", null, null, NOW.minusMinutes(1))));
    givenApprovalAvailable();
    givenMinimumRemainingTtl(30L);
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any()))
        .thenReturn(intentResult(RETRY_INTENT_ID, true, "CONFIRMED", null));

    assertThatThrownBy(() -> service.execute(command(USER_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("not signable");

    verify(saveSessionPort, never()).save(any());
    verify(cancelExecutionPort, never()).cancelIfSignable(any(), any(), any());
  }

  @Test
  void execute_whenPendingOnchainTransactionUnconfirmedAndTtlElapsed_returnsTerminalStatus() {
    WalletRegistrationSession pending = expiredPendingOnchainSession();
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(pending));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(
            Optional.of(state("PENDING_ONCHAIN", "UNCONFIRMED", null, NOW.minusSeconds(1))));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_FAILED);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.NONE);
    assertThat(result.lastErrorCode()).isEqualTo(WalletRegistrationReceiptTimeout.ERROR_CODE);
    verify(buildDraftPort, never()).build(any());
    verify(submitDraftPort, never()).submit(any());
  }

  @Test
  void execute_whenTerminalSession_rejectsRetry() {
    for (WalletRegistrationSession terminalSession : terminalSessions()) {
      when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
          .thenReturn(Optional.of(terminalSession));

      assertThatThrownBy(() -> service.execute(command(USER_ID)))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    verify(buildDraftPort, never()).build(any());
  }

  private static RetryWalletRegistrationApprovalCommand command(Long userId) {
    return new RetryWalletRegistrationApprovalCommand(userId, REGISTRATION_ID);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, USER_ID, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession expiredTtlSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID,
            USER_ID,
            "0x" + "a".repeat(40),
            "nonce-1",
            NOW.minusSeconds(1),
            NOW.minusMinutes(31))
        .attachApprovalIntent(INTENT_ID, NOW.minusSeconds(1), NOW.minusMinutes(30));
  }

  private static WalletRegistrationSession pendingOnchainSession() {
    return approvalRequiredSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "c".repeat(64), "SIGNED", NOW.plusSeconds(2))
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "c".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
  }

  private static WalletRegistrationSession expiredPendingOnchainSession() {
    return expiredTtlSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "c".repeat(64), "SIGNED", NOW.minusSeconds(20))
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "c".repeat(64), "PENDING_ONCHAIN", NOW.minusSeconds(10));
  }

  private static WalletRegistrationSession nearExpiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID,
            USER_ID,
            "0x" + "a".repeat(40),
            "nonce-1",
            NOW.plusSeconds(20),
            NOW.minusMinutes(1))
        .attachApprovalIntent(INTENT_ID, NOW.plusSeconds(20), NOW.minusSeconds(30));
  }

  private static List<WalletRegistrationSession> terminalSessions() {
    WalletRegistrationSession approvalRequired = approvalRequiredSession();
    WalletRegistrationSession pending =
        approvalRequired
            .markApprovalSigned(INTENT_ID, 10L, "0x" + "c".repeat(64), "SIGNED", NOW.plusSeconds(2))
            .markApprovalPendingOnchain(
                INTENT_ID, 10L, "0x" + "c".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
    return List.of(
        pending.markRegistered(1L, NOW.plusSeconds(4)),
        approvalRequired.markApprovalFailed("APPROVAL_FAILED", "failed", NOW.plusSeconds(2)),
        approvalRequired.expire("EXPIRED", "expired", NOW.plusSeconds(2)),
        approvalRequired.cancel("CANCELED", "canceled", NOW.plusSeconds(2)));
  }

  private static WalletApprovalExecutionStateView state(
      String executionStatus,
      String transactionStatus,
      WalletApprovalSignRequestBundle signRequest,
      LocalDateTime expiresAt) {
    return state(executionStatus, transactionStatus, signRequest, expiresAt, null);
  }

  private static WalletApprovalExecutionStateView state(
      String executionStatus,
      String transactionStatus,
      WalletApprovalSignRequestBundle signRequest,
      LocalDateTime expiresAt,
      String signRequestUnavailableReason) {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        REGISTRATION_ID,
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        INTENT_ID,
        executionStatus,
        expiresAt,
        1L,
        "EIP7702",
        2,
        signRequest,
        signRequestUnavailableReason,
        null,
        transactionStatus,
        null);
  }

  private static WalletApprovalSignRequestBundle signRequest() {
    return WalletApprovalSignRequestBundle.forEip7702(
        new WalletApprovalSignRequestBundle.AuthorizationSignRequest(
            10L, "0x" + "b".repeat(40), 7L, "0x" + "c".repeat(64)),
        new WalletApprovalSignRequestBundle.SubmitSignRequest("0x" + "d".repeat(64), 123L));
  }

  private static WalletApprovalExecutionDraft draft() {
    return draft(NOW.plusMinutes(5));
  }

  private static WalletApprovalExecutionDraft draft(LocalDateTime expiresAt) {
    return new WalletApprovalExecutionDraft(
        WalletApprovalExecutionResourceType.WALLET_REGISTRATION,
        REGISTRATION_ID,
        WalletApprovalExecutionResourceStatus.PENDING_EXECUTION,
        WalletApprovalExecutionActionType.WALLET_ESCROW_APPROVE,
        USER_ID,
        null,
        "wallet-registration-approval:" + REGISTRATION_ID,
        "0x" + "1".repeat(64),
        "{}",
        List.of(
            new WalletApprovalExecutionDraftCall(
                "0x" + "a".repeat(40), BigInteger.ZERO, "0x095ea7b3")),
        false,
        "0x" + "a".repeat(40),
        1L,
        "0x" + "b".repeat(40),
        "0x" + "2".repeat(64),
        null,
        null,
        expiresAt);
  }

  private static WalletApprovalExecutionIntentResult intentResult(String intentId) {
    return intentResult(intentId, false);
  }

  private static WalletApprovalExecutionIntentResult intentResult(
      String intentId, boolean existing) {
    return intentResult(intentId, existing, "AWAITING_SIGNATURE", signRequest());
  }

  private static WalletApprovalExecutionIntentResult intentResult(
      String intentId,
      boolean existing,
      String executionIntentStatus,
      WalletApprovalSignRequestBundle signRequest) {
    return new WalletApprovalExecutionIntentResult(
        new WalletApprovalExecutionIntentResult.Resource(
            "WALLET_REGISTRATION", REGISTRATION_ID, "PENDING_EXECUTION"),
        "WALLET_ESCROW_APPROVE",
        new WalletApprovalExecutionIntentResult.ExecutionIntent(
            intentId, executionIntentStatus, NOW.plusMinutes(5), 1L),
        new WalletApprovalExecutionIntentResult.Execution("EIP7702", 2),
        signRequest,
        existing);
  }

  private void givenApprovalAvailable() {
    when(loadWalletApprovalCapabilityPort.load()).thenReturn(WalletApprovalCapability.enabled());
  }

  private void givenMinimumRemainingTtl(long seconds) {
    when(loadWalletApprovalTtlPolicyPort.load()).thenReturn(new WalletApprovalTtlPolicy(seconds));
  }

  private static final class RecordingRetryTransactionPort
      implements RunWalletRegistrationRetryTransactionPort {

    private final AtomicBoolean active = new AtomicBoolean(false);

    @Override
    public <T> T execute(java.util.function.Supplier<T> callback) {
      active.set(true);
      try {
        return callback.get();
      } finally {
        active.set(false);
      }
    }

    boolean active() {
      return active.get();
    }
  }
}
