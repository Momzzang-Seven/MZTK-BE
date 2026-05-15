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
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionRequest;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.exception.DuplicateWalletRegistrationSessionException;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CreateWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeUsedPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
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
class RegisterWalletApprovalAttemptServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final Long USER_ID = 1L;
  private static final String WALLET_ADDRESS = "0x" + "a".repeat(40);
  private static final String NONCE = "nonce-1";
  private static final String SIGNATURE = "0x" + "b".repeat(130);

  @Mock private BuildWalletApprovalExecutionDraftPort buildDraftPort;
  @Mock private SubmitWalletApprovalExecutionDraftPort submitDraftPort;
  @Mock private CreateWalletRegistrationSessionPort createSessionPort;
  @Mock private MarkWalletRegistrationChallengeUsedPort markChallengeUsedPort;

  private RegisterWalletApprovalAttemptService service;

  @BeforeEach
  void setUp() {
    service =
        new RegisterWalletApprovalAttemptService(
            buildDraftPort,
            submitDraftPort,
            createSessionPort,
            markChallengeUsedPort,
            new TestWalletRegistrationPolicy(),
            CLOCK);
  }

  @Test
  void createPendingApproval_createsAttachedSessionAndMarksChallengeUsed() {
    when(buildDraftPort.build(any())).thenReturn(draft("registration-placeholder"));
    when(submitDraftPort.submit(any())).thenReturn(intentResult("registration-placeholder"));
    when(createSessionPort.createAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RegisterWalletResult result = service.createPendingApproval(command());

    ArgumentCaptor<WalletApprovalExecutionRequest> requestCaptor =
        ArgumentCaptor.forClass(WalletApprovalExecutionRequest.class);
    verify(buildDraftPort).build(requestCaptor.capture());
    assertThat(requestCaptor.getValue().expiresAt()).isEqualTo(NOW.plusMinutes(30));
    assertThat(result.status()).isEqualTo(WalletRegistrationStatus.APPROVAL_REQUIRED);
    assertThat(result.walletId()).isNull();
    assertThat(result.registrationId()).isNotBlank();
    assertThat(result.web3()).isNotNull();
    verify(createSessionPort)
        .createAndFlush(
            org.mockito.ArgumentMatchers.argThat(
                session ->
                    session.getLatestExecutionIntentId().equals("intent-1")
                        && session.getApprovalExpiresAt().equals(NOW.plusMinutes(30))));
    verify(markChallengeUsedPort).markUsed(NONCE);
  }

  @Test
  void createPendingApproval_whenSubmitFails_doesNotCreateSessionOrUseChallenge() {
    when(buildDraftPort.build(any())).thenReturn(draft("registration-placeholder"));
    when(submitDraftPort.submit(any())).thenThrow(new IllegalStateException("submit failed"));

    assertThatThrownBy(() -> service.createPendingApproval(command()))
        .isInstanceOf(IllegalStateException.class);

    verify(createSessionPort, never()).createAndFlush(any());
    verify(markChallengeUsedPort, never()).markUsed(any());
  }

  @Test
  void createPendingApproval_whenSessionCreateRaces_doesNotUseChallenge() {
    when(buildDraftPort.build(any())).thenReturn(draft("registration-placeholder"));
    when(submitDraftPort.submit(any())).thenReturn(intentResult("registration-placeholder"));
    when(createSessionPort.createAndFlush(any()))
        .thenThrow(
            new DuplicateWalletRegistrationSessionException(
                USER_ID, WALLET_ADDRESS, new RuntimeException("duplicate")));

    assertThatThrownBy(() -> service.createPendingApproval(command()))
        .isInstanceOf(DuplicateWalletRegistrationSessionException.class);

    verify(markChallengeUsedPort, never()).markUsed(any());
  }

  private static RegisterWalletCommand command() {
    return new RegisterWalletCommand(USER_ID, WALLET_ADDRESS, SIGNATURE, NONCE);
  }

  private static WalletApprovalExecutionDraft draft(String registrationId) {
    return new WalletApprovalExecutionDraft(
        WalletApprovalExecutionResourceType.WALLET_REGISTRATION,
        registrationId,
        WalletApprovalExecutionResourceStatus.PENDING_EXECUTION,
        WalletApprovalExecutionActionType.WALLET_ESCROW_APPROVE,
        USER_ID,
        null,
        "wallet-registration-approval:" + registrationId,
        "0x" + "1".repeat(64),
        "{}",
        List.of(
            new WalletApprovalExecutionDraftCall(WALLET_ADDRESS, BigInteger.ZERO, "0x095ea7b3")),
        false,
        WALLET_ADDRESS,
        1L,
        "0x" + "c".repeat(40),
        "0x" + "2".repeat(64),
        null,
        null,
        NOW.plusMinutes(5));
  }

  private static WalletApprovalExecutionIntentResult intentResult(String registrationId) {
    return new WalletApprovalExecutionIntentResult(
        new WalletApprovalExecutionIntentResult.Resource(
            "WALLET_REGISTRATION", registrationId, "PENDING_EXECUTION"),
        "WALLET_ESCROW_APPROVE",
        new WalletApprovalExecutionIntentResult.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", NOW.plusMinutes(5), 1L),
        new WalletApprovalExecutionIntentResult.Execution("EIP7702", 2),
        WalletApprovalSignRequestBundle.forEip7702(
            new WalletApprovalSignRequestBundle.AuthorizationSignRequest(
                10L, "0x" + "c".repeat(40), 1L, "0x" + "3".repeat(64)),
            new WalletApprovalSignRequestBundle.SubmitSignRequest("0x" + "4".repeat(64), 123L)),
        false);
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
