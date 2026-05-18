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
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpiredWalletRegistrationSessionResult;
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
class WalletRegistrationSessionExpiryProcessorTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDateTime NOW =
      LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone());
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Mock private LockWalletRegistrationSessionPort lockSessionPort;
  @Mock private SaveWalletRegistrationSessionPort saveSessionPort;

  private WalletRegistrationSessionExpiryProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new WalletRegistrationSessionExpiryProcessor(lockSessionPort, saveSessionPort, CLOCK);
  }

  @Test
  void expire_whenPreSubmissionTtlElapsed_marksExpiredUnderLock() {
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID))
        .thenReturn(Optional.of(expiredTtlSession()));

    ExpiredWalletRegistrationSessionResult result = processor.expire(command());

    ArgumentCaptor<WalletRegistrationSession> captor =
        ArgumentCaptor.forClass(WalletRegistrationSession.class);
    verify(saveSessionPort).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(WalletRegistrationStatus.EXPIRED);
    assertThat(result.expired()).isTrue();
    assertThat(result.canceledExecutionIntentId()).isEqualTo(INTENT_ID);
  }

  @Test
  void expire_whenPendingOnchain_doesNotExpire() {
    WalletRegistrationSession pending =
        activeTtlSession()
            .markApprovalSigned(INTENT_ID, 10L, "0x" + "b".repeat(64), "SIGNED", NOW.plusSeconds(2))
            .markApprovalPendingOnchain(
                INTENT_ID, 10L, "0x" + "b".repeat(64), "PENDING_ONCHAIN", NOW.plusSeconds(3));
    when(lockSessionPort.lockByPublicIdForUpdate(REGISTRATION_ID)).thenReturn(Optional.of(pending));

    ExpiredWalletRegistrationSessionResult result = processor.expire(command());

    assertThat(result.expired()).isFalse();
    verify(saveSessionPort, never()).save(org.mockito.ArgumentMatchers.any());
  }

  private static ExpireWalletRegistrationSessionCommand command() {
    return new ExpireWalletRegistrationSessionCommand(REGISTRATION_ID);
  }

  private static WalletRegistrationSession activeTtlSession() {
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
}
