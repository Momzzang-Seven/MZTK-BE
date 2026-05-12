package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationNextAction;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
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
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final Long USER_ID = 1L;

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;
  @Mock private LoadWalletApprovalExecutionStatePort loadExecutionStatePort;

  private GetWalletRegistrationStatusService service;

  @BeforeEach
  void setUp() {
    service = new GetWalletRegistrationStatusService(loadSessionPort, loadExecutionStatePort);
  }

  @Test
  void execute_whenWrongUser_doesNotLoadExecutionState() {
    when(loadSessionPort.loadByPublicIdAndUserId(REGISTRATION_ID, 2L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.execute(new GetWalletRegistrationStatusQuery(2L, REGISTRATION_ID)))
        .isInstanceOf(WalletNotFoundException.class);

    verify(loadExecutionStatePort, never())
        .loadByExecutionIntentId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenApprovalRequiredAndSignable_returnsRecoverableWeb3() {
    when(loadSessionPort.loadByPublicIdAndUserId(REGISTRATION_ID, USER_ID))
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
    when(loadSessionPort.loadByPublicIdAndUserId(REGISTRATION_ID, USER_ID))
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

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, USER_ID, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
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

  private static WalletApprovalExecutionStateView state(
      String executionStatus,
      String transactionStatus,
      WalletApprovalSignRequestBundle signRequest) {
    return new WalletApprovalExecutionStateView(
        "WALLET_REGISTRATION",
        REGISTRATION_ID,
        "PENDING_EXECUTION",
        "WALLET_ESCROW_APPROVE",
        INTENT_ID,
        executionStatus,
        NOW.plusMinutes(5),
        "EIP7702",
        2,
        signRequest,
        null,
        transactionStatus,
        null);
  }
}
