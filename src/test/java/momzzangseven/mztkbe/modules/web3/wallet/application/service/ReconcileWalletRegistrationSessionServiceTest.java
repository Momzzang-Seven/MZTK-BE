package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationFinalizationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ExpireWalletRegistrationSessionUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalSubmittedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalTerminatedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationFinalizationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SyncWalletApprovalExecutionSuccessPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconcileWalletRegistrationSessionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  @Mock private MarkWalletRegistrationApprovalSubmittedUseCase markSubmittedUseCase;
  @Mock private MarkWalletRegistrationApprovalTerminatedUseCase markTerminatedUseCase;
  @Mock private FinalizeWalletRegistrationUseCase finalizeUseCase;
  @Mock private RetryWalletRegistrationFinalizationUseCase retryFinalizationUseCase;
  @Mock private ExpireWalletRegistrationSessionUseCase expireUseCase;
  @Mock private SyncWalletApprovalExecutionSuccessPort syncExecutionSuccessPort;

  private ReconcileWalletRegistrationSessionService service;

  @BeforeEach
  void setUp() {
    service =
        new ReconcileWalletRegistrationSessionService(
            loadSessionPort,
            loadExecutionStatePort,
            markSubmittedUseCase,
            markTerminatedUseCase,
            finalizeUseCase,
            retryFinalizationUseCase,
            expireUseCase,
            syncExecutionSuccessPort,
            new TestWalletRegistrationPolicy(),
            CLOCK);
  }

  @Test
  void execute_whenTransactionSucceededButIntentPending_syncsSuccessWithoutSessionMutation() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(pendingOnchainSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "SUCCEEDED", 10L, NOW.plusMinutes(5))));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(syncExecutionSuccessPort).syncSucceededTransaction(10L);
    verify(markSubmittedUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenExecutionConfirmed_retriesLocalFinalization() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(pendingOnchainSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("CONFIRMED", "SUCCEEDED", 10L, NOW.plusMinutes(5))));

    service.execute(command());

    verify(finalizeUseCase)
        .execute(new FinalizeWalletRegistrationCommand(REGISTRATION_ID, INTENT_ID));
  }

  @Test
  void execute_whenSignedStateBecamePending_marksSubmittedThroughHookUseCase() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.of(signedSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "PENDING", 10L, NOW.plusMinutes(5))));

    service.execute(command());

    verify(markSubmittedUseCase)
        .execute(
            new MarkWalletRegistrationApprovalSubmittedCommand(
                REGISTRATION_ID, INTENT_ID, "PENDING"));
  }

  @Test
  void execute_whenTransactionFailedWhileIntentPending_marksTerminatedBeforeSubmitted() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(pendingOnchainSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(
            Optional.of(state("PENDING_ONCHAIN", "FAILED_ONCHAIN", 10L, NOW.plusMinutes(5))));

    service.execute(command());

    verify(markTerminatedUseCase)
        .execute(
            new MarkWalletRegistrationApprovalTerminatedCommand(
                REGISTRATION_ID,
                INTENT_ID,
                "FAILED_ONCHAIN",
                "approval transaction failed on-chain"));
    verify(markSubmittedUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenTransactionUnconfirmed_marksReceiptTimeoutBeforeSubmitted() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(pendingOnchainSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "UNCONFIRMED", 10L, NOW.plusMinutes(5))));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(markTerminatedUseCase)
        .execute(
            new MarkWalletRegistrationApprovalTerminatedCommand(
                REGISTRATION_ID,
                INTENT_ID,
                WalletRegistrationReceiptTimeout.ERROR_CODE,
                WalletRegistrationReceiptTimeout.ERROR_REASON));
    verify(markSubmittedUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
    verify(expireUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenSignRequestExpired_marksTerminatedAsExpired() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("AWAITING_SIGNATURE", null, null, NOW.minusSeconds(1))));

    service.execute(command());

    verify(markTerminatedUseCase)
        .execute(
            new MarkWalletRegistrationApprovalTerminatedCommand(
                REGISTRATION_ID, INTENT_ID, "EXPIRED", "EXPIRED"));
  }

  @Test
  void execute_whenSessionTtlElapsedAndExecutionStillAwaitingSignature_usesExpiryUseCase() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("AWAITING_SIGNATURE", null, null, NOW.minusSeconds(1))));
    when(expireUseCase.execute(new ExpireWalletRegistrationSessionCommand(REGISTRATION_ID)))
        .thenReturn(true);

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(markTerminatedUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenSessionTtlElapsedButExecutionConfirmed_finalizesBeforeExpiring() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("CONFIRMED", "SUCCEEDED", 10L, NOW.minusSeconds(1))));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(finalizeUseCase)
        .execute(new FinalizeWalletRegistrationCommand(REGISTRATION_ID, INTENT_ID));
    verify(expireUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenSessionTtlElapsedButTransactionSucceeded_syncsSuccessBeforeExpiring() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "SUCCEEDED", 10L, NOW.minusSeconds(1))));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(syncExecutionSuccessPort).syncSucceededTransaction(10L);
    verify(expireUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenSessionTtlElapsedButSubmissionPending_marksSubmittedBeforeExpiring() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(1L, INTENT_ID))
        .thenReturn(Optional.of(state("PENDING_ONCHAIN", "PENDING", 10L, NOW.minusSeconds(1))));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(markSubmittedUseCase)
        .execute(
            new MarkWalletRegistrationApprovalSubmittedCommand(
                REGISTRATION_ID, INTENT_ID, "PENDING"));
    verify(expireUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenFinalizationFailedAfterBackoff_retriesFinalization() {
    WalletRegistrationSession session =
        pendingOnchainSession()
            .markFinalizationFailed("FINALIZATION_FAILED", "db", NOW.minusMinutes(2));
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.of(session));

    service.execute(command());

    verify(retryFinalizationUseCase)
        .execute(new RetryWalletRegistrationFinalizationCommand(REGISTRATION_ID));
  }

  @Test
  void execute_whenLocalConflictAfterBackoff_retriesFinalization() {
    WalletRegistrationSession session =
        pendingOnchainSession()
            .markLocalConflict("LOCAL_CONFLICT", "active wallet", NOW.minusMinutes(2));
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.of(session));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.recovered()).isTrue();
    verify(retryFinalizationUseCase)
        .execute(new RetryWalletRegistrationFinalizationCommand(REGISTRATION_ID));
    verify(loadExecutionStatePort, never())
        .loadByExecutionIntentId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenLocalConflictBeforeBackoff_skipsFinalizationRetry() {
    WalletRegistrationSession session =
        pendingOnchainSession()
            .markLocalConflict("LOCAL_CONFLICT", "active wallet", NOW.minusSeconds(30));
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.of(session));

    ReconcileWalletRegistrationSessionResult result = service.execute(command());

    assertThat(result.skipped()).isTrue();
    verify(retryFinalizationUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
    verify(loadExecutionStatePort, never())
        .loadByExecutionIntentId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  private static ReconcileWalletRegistrationSessionCommand command() {
    return new ReconcileWalletRegistrationSessionCommand(REGISTRATION_ID);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession expiredTtlSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID,
            1L,
            "0x" + "a".repeat(40),
            "nonce-1",
            NOW.minusSeconds(1),
            NOW.minusMinutes(31))
        .attachApprovalIntent(INTENT_ID, NOW.minusSeconds(1), NOW.minusMinutes(30));
  }

  private static WalletRegistrationSession signedSession() {
    return approvalRequiredSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(2));
  }

  private static WalletRegistrationSession pendingOnchainSession() {
    return signedSession()
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
  }

  private static WalletApprovalExecutionStateView state(
      String executionStatus,
      String transactionStatus,
      Long transactionId,
      LocalDateTime expiresAt) {
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
        null,
        null,
        transactionId,
        transactionStatus,
        transactionId == null ? null : "0x" + "c".repeat(64));
  }

  private static final class TestWalletRegistrationPolicy
      implements LoadWalletRegistrationPolicyPort {

    @Override
    public int sessionTtlSeconds() {
      return 1800;
    }

    @Override
    public int finalizationRetryBackoffSeconds() {
      return 60;
    }
  }
}
