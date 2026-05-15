package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.KmsAuditRecorder;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletKeyReplacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletKeyReplacedKmsHandlerTest {

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;

  @InjectMocks private TreasuryWalletKeyReplacedKmsHandler handler;

  @Test
  void disposeTrue_callsUpdateAliasAndDisableAndScheduleDeletionAndAudits() {
    TreasuryWalletKeyReplacedEvent event =
        new TreasuryWalletKeyReplacedEvent(
            "reward-treasury", "old-kms", "new-kms", "0x" + "a".repeat(40), 1L, true);

    handler.on(event);

    verify(kmsKeyLifecyclePort).updateAlias("reward-treasury", "new-kms");
    verify(kmsAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq("new-kms"),
            eq(event.walletAddress()),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(true),
            eq(null));
    verify(kmsKeyLifecyclePort).disableKey("old-kms");
    verify(kmsAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq("old-kms"),
            eq(event.walletAddress()),
            eq(KmsAuditAction.KMS_DISABLE),
            eq(true),
            eq(null));
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("old-kms"), eq(7));
    verify(kmsAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq("old-kms"),
            eq(event.walletAddress()),
            eq(KmsAuditAction.KMS_SCHEDULE_DELETION),
            eq(true),
            eq(null));
  }

  @Test
  void disposeFalse_callsUpdateAliasOnlyAndSkipsOldKeyDisposal() {
    TreasuryWalletKeyReplacedEvent event =
        new TreasuryWalletKeyReplacedEvent(
            "reward-treasury", "old-kms", "new-kms", "0x" + "a".repeat(40), 1L, false);

    handler.on(event);

    verify(kmsKeyLifecyclePort).updateAlias("reward-treasury", "new-kms");
    verify(kmsKeyLifecyclePort, never()).disableKey(any());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(any(), anyInt());
  }

  @Test
  void updateAliasFailure_recordsAuditAndSwallows() {
    TreasuryWalletKeyReplacedEvent event =
        new TreasuryWalletKeyReplacedEvent(
            "reward-treasury", "old-kms", "new-kms", "0x" + "a".repeat(40), 1L, true);
    doThrow(new RuntimeException("aws boom"))
        .when(kmsKeyLifecyclePort)
        .updateAlias("reward-treasury", "new-kms");

    handler.on(event);

    verify(kmsAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq("new-kms"),
            any(),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(false),
            eq("RuntimeException"));
    verify(kmsKeyLifecyclePort).disableKey("old-kms");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq("old-kms"), eq(7));
  }
}
