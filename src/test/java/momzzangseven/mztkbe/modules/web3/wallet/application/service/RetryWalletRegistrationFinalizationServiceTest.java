package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationFinalizationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetryWalletRegistrationFinalizationServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";
  private static final Long USER_ID = 7L;

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;
  @Mock private FinalizeWalletRegistrationUseCase finalizeUseCase;

  private RetryWalletRegistrationFinalizationService service;

  @BeforeEach
  void setUp() {
    service = new RetryWalletRegistrationFinalizationService(loadSessionPort, finalizeUseCase);
  }

  @Test
  void execute_whenSessionMissing_doesNothing() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.empty());

    service.execute(command());

    verify(finalizeUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenStatusIsNotConfirmedButUnfinalized_doesNothing() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    service.execute(command());

    verify(finalizeUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenLatestExecutionIntentMissing_doesNothing() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(finalizationFailedSessionWithoutIntent()));

    service.execute(command());

    verify(finalizeUseCase, never()).execute(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void execute_whenConfirmedButNotFinalized_delegatesToFinalization() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(finalizationFailedSession()));

    service.execute(command());

    ArgumentCaptor<FinalizeWalletRegistrationCommand> captor =
        ArgumentCaptor.forClass(FinalizeWalletRegistrationCommand.class);
    verify(finalizeUseCase).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().registrationId())
        .isEqualTo(REGISTRATION_ID);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().executionIntentId())
        .isEqualTo(INTENT_ID);
  }

  private static RetryWalletRegistrationFinalizationCommand command() {
    return new RetryWalletRegistrationFinalizationCommand(REGISTRATION_ID);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, USER_ID, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession finalizationFailedSession() {
    return approvalRequiredSession()
        .markApprovalSigned(INTENT_ID, 10L, "0x" + "c".repeat(64), "SIGNED", NOW.plusSeconds(2))
        .markApprovalPendingOnchain(
            INTENT_ID, 10L, "0x" + "c".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3))
        .markFinalizationFailed(
            "FINALIZATION_FAILED", "local finalization failed", NOW.plusSeconds(4));
  }

  private static WalletRegistrationSession finalizationFailedSessionWithoutIntent() {
    return WalletRegistrationSession.builder()
        .publicId(REGISTRATION_ID)
        .userId(USER_ID)
        .walletAddress("0x" + "a".repeat(40))
        .challengeNonce("nonce-1")
        .status(WalletRegistrationStatus.FINALIZATION_FAILED)
        .retryCount(0)
        .approvalExpiresAt(NOW.plusMinutes(30))
        .createdAt(NOW)
        .updatedAt(NOW)
        .build();
  }
}
