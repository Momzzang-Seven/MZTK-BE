package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LockWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SaveWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationFinalizationFailureRecorderTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;

  private WalletRegistrationFinalizationFailureRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder =
        new WalletRegistrationFinalizationFailureRecorder(lockSessionPort, saveSessionPort, CLOCK);
  }

  @Test
  void recordLocalConflict_locksSessionAndPersistsLocalConflict() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    recorder.recordLocalConflict(command(), "LOCAL_CONFLICT", "active wallet");

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.LOCAL_CONFLICT);
    assertThat(captor.getValue().getLastErrorReason()).isEqualTo("active wallet");
  }

  @Test
  void recordUnexpectedFailure_doesNotOverwriteStaleIntentSession() {
    WalletRegistrationSession session =
        WalletRegistrationSession.create(
                REGISTRATION_ID, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
            .attachApprovalIntent("new-intent", NOW.plusMinutes(30), NOW.plusSeconds(1));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(session));

    recorder.recordUnexpectedFailure(command(), "FINALIZATION_FAILED", "db failed");

    verify(saveSessionPort, never()).save(any());
  }

  private static FinalizeWalletRegistrationCommand command() {
    return new FinalizeWalletRegistrationCommand(REGISTRATION_ID, INTENT_ID);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }
}
