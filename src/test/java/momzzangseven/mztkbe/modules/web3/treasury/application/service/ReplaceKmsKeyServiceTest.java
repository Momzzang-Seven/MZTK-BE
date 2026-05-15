package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplaceKmsKeyServiceTest {

  private static final String ALIAS = "reward-treasury";
  private static final String OLD_KEY = "old-kms";
  private static final String NEW_KEY = "new-kms";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 1L;

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;

  private ReplaceKmsKeyService service;

  @BeforeEach
  void setUp() {
    service = new ReplaceKmsKeyService(kmsKeyLifecyclePort, kmsAuditRecorder);
  }

  @Test
  void disposeTrue_runsUpdateAliasAndDisableAndScheduleDeletion() {
    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, true));

    verify(kmsKeyLifecyclePort).updateAlias(ALIAS, NEW_KEY);
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(true),
            eq(null));
    verify(kmsKeyLifecyclePort).disableKey(OLD_KEY);
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(OLD_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_DISABLE),
            eq(true),
            eq(null));
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq(OLD_KEY), eq(7));
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(OLD_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_SCHEDULE_DELETION),
            eq(true),
            eq(null));
  }

  @Test
  void disposeFalse_runsUpdateAliasOnly_skipsOldKeyDisposal() {
    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, false));

    verify(kmsKeyLifecyclePort).updateAlias(ALIAS, NEW_KEY);
    verify(kmsKeyLifecyclePort, never()).disableKey(any());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(any(), anyInt());
  }

  @Test
  void updateAliasFailure_recordsAuditAndStillProceedsWithOldKeyDisposal() {
    doThrow(new RuntimeException("aws boom")).when(kmsKeyLifecyclePort).updateAlias(ALIAS, NEW_KEY);

    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, true));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(false),
            eq("RuntimeException"));
    verify(kmsKeyLifecyclePort).disableKey(OLD_KEY);
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(eq(OLD_KEY), eq(7));
  }
}
