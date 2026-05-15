package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.service.KmsAuditRecorder;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletReactivatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletReactivatedKmsHandlerTest {

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;

  @InjectMocks private TreasuryWalletReactivatedKmsHandler handler;

  @Test
  void onReactivated_callsEnableKeyAndRecordsAudit() {
    TreasuryWalletReactivatedEvent event =
        new TreasuryWalletReactivatedEvent("reward-treasury", "kms-id", "0x" + "a".repeat(40), 1L);

    handler.on(event);

    verify(kmsKeyLifecyclePort).enableKey("kms-id");
    verify(kmsAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq("kms-id"),
            eq(event.walletAddress()),
            eq(KmsAuditAction.KMS_ENABLE),
            eq(true),
            eq(null));
  }

  @Test
  void enableKeyFailure_recordsFailureAuditAndSwallows() {
    TreasuryWalletReactivatedEvent event =
        new TreasuryWalletReactivatedEvent("reward-treasury", "kms-id", "0x" + "a".repeat(40), 1L);
    doThrow(new RuntimeException("aws boom")).when(kmsKeyLifecyclePort).enableKey("kms-id");

    handler.on(event);

    verify(kmsAuditRecorder)
        .record(
            eq(1L),
            eq("reward-treasury"),
            eq("kms-id"),
            any(),
            eq(KmsAuditAction.KMS_ENABLE),
            eq(false),
            eq("RuntimeException"));
  }
}
