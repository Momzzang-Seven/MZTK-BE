package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationNextAction;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationReceiptTimeout;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetWalletRegistrationStatusServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW.atZone(APP_ZONE).toInstant(), APP_ZONE);
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final String EIP7702_DEADLINE_TOO_CLOSE = "EIP7702_DEADLINE_TOO_CLOSE";
  private static final Long USER_ID = 1L;

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadExecutionStatePort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;

  private GetWalletRegistrationStatusService service;

  @BeforeEach
  void setUp() {
    service =
        new GetWalletRegistrationStatusService(
            lockSessionPort,
            loadExecutionStatePort,
            new WalletRegistrationReceiptTimeoutMarker(saveSessionPort),
            FIXED_CLOCK);
  }

  @Test
  void execute_whenWrongUser_doesNotLoadExecutionState() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new GetWalletRegistrationStatusQuery(2L, REGISTRATION_ID)))
        .isInstanceOf(WalletNotFoundException.class);

    verify(loadExecutionStatePort, never())
        .loadByExecutionIntentId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenApprovalRequiredAndSignable_returnsRecoverableWeb3() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(signableState()));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().signRequest().authorization().authorityNonce()).isEqualTo(7L);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.SIGN_APPROVAL);
  }

  @Test
  void execute_usesSessionErrorMetadataWithoutMutatingExpiredReadableState() {
    WalletRegistrationSession session =
        approvalRequiredSession().markApprovalRetryable("EXPIRED", "sign request expired", NOW);
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(session));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(expiredState()));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.web3()).isNull();
    assertThat(result.lastErrorCode()).isEqualTo("EXPIRED");
    assertThat(result.lastErrorReason()).isEqualTo("sign request expired");
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.RETRY_APPROVAL);
  }

  @Test
  void execute_whenApprovalRequiredDeadlineTooClose_exposesRetryReason() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(deadlineTooCloseState()));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().signRequest()).isNull();
    assertThat(result.signRequestUnavailableReason()).isEqualTo(EIP7702_DEADLINE_TOO_CLOSE);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.RETRY_APPROVAL);
  }

  @Test
  void execute_whenApprovalRequiredSessionTtlElapsed_returnsExpiredView() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredApprovalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(signableState()));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.EXPIRED);
    assertThat(result.web3()).isNull();
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.NONE);
  }

  @Test
  void execute_whenApprovalRetryableSessionTtlElapsed_returnsExpiredView() {
    WalletRegistrationSession session =
        approvalRequiredSession()
            .markApprovalRetryable("EXPIRED", "retryable but ttl elapsed", NOW.minusSeconds(5))
            .toBuilder()
            .approvalExpiresAt(NOW.minusSeconds(1))
            .build();
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(session));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(expiredState()));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.EXPIRED);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.NONE);
  }

  @Test
  void execute_whenApprovalRequiredButExecutionSubmitted_waitsForTransaction() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(submittedState()));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.web3()).isNull();
    assertThat(result.nextAction())
        .isEqualTo(WalletRegistrationNextAction.WAIT_FOR_APPROVAL_TRANSACTION);
  }

  @Test
  void execute_whenReceiptTimeoutDetected_marksSponsorNonceBlocked() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalPendingOnchainSession()));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, INTENT_ID))
        .thenReturn(Optional.of(receiptTimeoutState()));
    when(saveSessionPort.save(any(WalletRegistrationSession.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.SPONSOR_NONCE_BLOCKED);
    assertThat(result.nextAction()).isEqualTo(WalletRegistrationNextAction.CONTACT_SUPPORT);
    assertThat(result.lastErrorCode()).isEqualTo(WalletRegistrationReceiptTimeout.ERROR_CODE);
    assertThat(result.supportMessageKey()).isEqualTo("WALLET_APPROVAL_OPERATOR_REVIEW");
    verify(lockSessionPort).lockByPublicIdForUpdate(REGISTRATION_ID);
    verify(saveSessionPort).save(any(WalletRegistrationSession.class));
  }

  @Test
  void execute_whenLockedSessionHasNewRetryIntent_doesNotOverwriteRetrySession() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession("intent-2")));
    when(loadExecutionStatePort.loadByExecutionIntentId(USER_ID, "intent-2"))
        .thenReturn(Optional.empty());

    WalletRegistrationStatusResult result =
        service.execute(new GetWalletRegistrationStatusQuery(USER_ID, REGISTRATION_ID));

    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    verify(saveSessionPort, never()).save(any(WalletRegistrationSession.class));
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return approvalRequiredSession(INTENT_ID);
  }

  private static WalletRegistrationSession approvalRequiredSession(String intentId) {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, USER_ID, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(intentId, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession expiredApprovalRequiredSession() {
    return approvalRequiredSession().toBuilder().approvalExpiresAt(NOW.minusSeconds(1)).build();
  }

  private static WalletRegistrationSession approvalPendingOnchainSession() {
    return approvalRequiredSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.minusSeconds(2))
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.minusSeconds(1));
  }

  private static WalletApprovalExecutionStateView signableState() {
    return state(
        "AWAITING_SIGNATURE",
        null,
        WalletApprovalSignRequestBundle.forEip7702(
            new WalletApprovalSignRequestBundle.AuthorizationSignRequest(
                10L, "0x" + "b".repeat(40), 7L, "0x" + "c".repeat(64)),
            new WalletApprovalSignRequestBundle.SubmitSignRequest("0x" + "d".repeat(64), 123L)));
  }

  private static WalletApprovalExecutionStateView expiredState() {
    return state("EXPIRED", null, null);
  }

  private static WalletApprovalExecutionStateView deadlineTooCloseState() {
    return state("AWAITING_SIGNATURE", null, null, EIP7702_DEADLINE_TOO_CLOSE);
  }

  private static WalletApprovalExecutionStateView submittedState() {
    return state("SIGNED", "SIGNED", null);
  }

  private static WalletApprovalExecutionStateView receiptTimeoutState() {
    return state("PENDING_ONCHAIN", WalletRegistrationReceiptTimeout.TRANSACTION_STATUS, null);
  }

  private static WalletApprovalExecutionStateView state(
      String executionStatus,
      String transactionStatus,
      WalletApprovalSignRequestBundle signRequest) {
    return state(executionStatus, transactionStatus, signRequest, null);
  }

  private static WalletApprovalExecutionStateView state(
      String executionStatus,
      String transactionStatus,
      WalletApprovalSignRequestBundle signRequest,
      String signRequestUnavailableReason) {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        REGISTRATION_ID,
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        INTENT_ID,
        executionStatus,
        NOW.plusMinutes(5),
        1L,
        "EIP7702",
        2,
        signRequest,
        signRequestUnavailableReason,
        null,
        transactionStatus,
        null);
  }
}
