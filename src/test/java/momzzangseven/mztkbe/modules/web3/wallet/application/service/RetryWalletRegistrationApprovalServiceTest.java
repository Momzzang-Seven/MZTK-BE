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
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
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
  private static final Long USER_ID = 1L;

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  @Mock private BuildWalletApprovalExecutionDraftPort buildDraftPort;
  @Mock private SubmitWalletApprovalExecutionDraftPort submitDraftPort;

  private RetryWalletRegistrationApprovalService service;

  @BeforeEach
  void setUp() {
    service =
        new RetryWalletRegistrationApprovalService(
            lockSessionPort,
            saveSessionPort,
            loadExecutionStatePort,
            buildDraftPort,
            submitDraftPort,
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
    when(buildDraftPort.build(any())).thenReturn(draft());
    when(submitDraftPort.submit(any())).thenReturn(intentResult(RETRY_INTENT_ID));
    when(saveSessionPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getLatestExecutionIntentId()).isEqualTo(RETRY_INTENT_ID);
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().executionIntent().id()).isEqualTo(RETRY_INTENT_ID);
  }

  @Test
  void execute_whenApprovalRequiredStillSignable_reusesCurrentSignRequest() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(
            Optional.of(state("AWAITING_SIGNATURE", null, signRequest(), NOW.plusMinutes(5))));

    WalletRegistrationStatusResult result = service.execute(command(USER_ID));

    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().executionIntent().id()).isEqualTo(INTENT_ID);
    verify(buildDraftPort, never()).build(any());
    verify(submitDraftPort, never()).submit(any());
    verify(saveSessionPort, never()).save(any());
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
        null,
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
        NOW.plusMinutes(5));
  }

  private static WalletApprovalExecutionIntentResult intentResult(String intentId) {
    return new WalletApprovalExecutionIntentResult(
        new WalletApprovalExecutionIntentResult.Resource(
            "WALLET_REGISTRATION", REGISTRATION_ID, "PENDING_EXECUTION"),
        "WALLET_ESCROW_APPROVE",
        new WalletApprovalExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", NOW.plusMinutes(5), 1L),
        new WalletApprovalExecutionIntentResult.Execution("EIP7702", 2),
        signRequest(),
        false);
  }
}
