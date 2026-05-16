package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;

  private ReplaceKmsKeyService service;

  @BeforeEach
  void setUp() {
    service =
        new ReplaceKmsKeyService(kmsKeyLifecyclePort, kmsAuditRecorder, loadTreasuryWalletPort);
  }

  private TreasuryWallet wallet(String kmsKeyId, TreasuryWalletStatus status) {
    return TreasuryWallet.builder()
        .walletAlias(ALIAS)
        .kmsKeyId(kmsKeyId)
        .walletAddress(ADDRESS)
        .status(status)
        .build();
  }

  @Test
  void disposeTrue_runsUpdateAliasAndDisableAndScheduleDeletion() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(NEW_KEY, TreasuryWalletStatus.ACTIVE)));

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

    InOrder inOrder = inOrder(kmsKeyLifecyclePort);
    inOrder.verify(kmsKeyLifecyclePort).updateAlias(ALIAS, NEW_KEY);
    inOrder.verify(kmsKeyLifecyclePort).disableKey(OLD_KEY);
    inOrder.verify(kmsKeyLifecyclePort).scheduleKeyDeletion(OLD_KEY, 7);
  }

  @Test
  void disposeFalse_runsUpdateAliasOnly_skipsOldKeyDisposal() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(NEW_KEY, TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, false));

    verify(kmsKeyLifecyclePort).updateAlias(ALIAS, NEW_KEY);
    verify(kmsKeyLifecyclePort, never()).disableKey(any());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(any(), anyInt());
  }

  @Test
  void updateAliasFailure_recordsAuditAndPropagates_andSkipsDisposal() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(NEW_KEY, TreasuryWalletStatus.ACTIVE)));
    doThrow(new RuntimeException("aws boom")).when(kmsKeyLifecyclePort).updateAlias(ALIAS, NEW_KEY);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, true)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("aws boom");

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(false),
            eq("RuntimeException"));
    verify(kmsKeyLifecyclePort, never()).disableKey(any());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(any(), anyInt());
  }

  @Test
  void staleSkip_rowMissing_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS)).thenReturn(Optional.empty());

    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, false));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("ROW_MISSING"));
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }

  @Test
  void staleSkip_keyNull_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(null, TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, false));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("KEY_NULL"));
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }

  @Test
  void staleSkip_keyIdMismatch_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("DIFFERENT_KEY", TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, false));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("KEY_ID_MISMATCH"));
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }

  @Test
  void staleSkip_statusMismatch_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(NEW_KEY, TreasuryWalletStatus.DISABLED)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, false));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("STATUS_MISMATCH"));
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }

  @Test
  void disableOldKeyFailure_recordsAuditAndPropagates_andSkipsScheduleDeletion() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(NEW_KEY, TreasuryWalletStatus.ACTIVE)));
    doThrow(new RuntimeException("disable boom")).when(kmsKeyLifecyclePort).disableKey(OLD_KEY);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, true)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("disable boom");

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(true),
            eq(null));
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(OLD_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_DISABLE),
            eq(false),
            eq("RuntimeException"));
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
  }

  @Test
  void scheduleKeyDeletionFailure_recordsAuditAndPropagates() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(NEW_KEY, TreasuryWalletStatus.ACTIVE)));
    doThrow(new RuntimeException("schedule boom"))
        .when(kmsKeyLifecyclePort)
        .scheduleKeyDeletion(OLD_KEY, 7);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ReplaceKmsKeyCommand(ALIAS, OLD_KEY, NEW_KEY, ADDRESS, OPERATOR_ID, true)))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("schedule boom");

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(NEW_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_UPDATE_ALIAS),
            eq(true),
            eq(null));
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(OLD_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_DISABLE),
            eq(true),
            eq(null));
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq(OLD_KEY),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_SCHEDULE_DELETION),
            eq(false),
            eq("RuntimeException"));
  }

  @Test
  void staleSkip_orphanOldKey_disposesOldKey_whenOldKeyDiffersFromCurrent() {
    // current row's kmsKeyId == "K2" (not "K1" nor "K0") → KEY_ID_MISMATCH.
    // command oldKey="K0" differs from current "K2" → orphan dispose path runs.
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("K2", TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, "K0", "K1", ADDRESS, OPERATOR_ID, true));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K1"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("KEY_ID_MISMATCH"));
    verify(kmsKeyLifecyclePort).disableKey("K0");
    verify(kmsKeyLifecyclePort).scheduleKeyDeletion("K0", 7);
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K0"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_DISABLE),
            eq(true),
            eq("ORPHAN_FROM_STALE_REPLACE"));
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K0"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_SCHEDULE_DELETION),
            eq(true),
            eq("ORPHAN_FROM_STALE_REPLACE"));
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
  }

  @Test
  void staleSkip_orphanOldKey_skipsWhenOldKeyEqualsCurrentKey() {
    // current row's kmsKeyId == "K0" matches command.oldKmsKeyId.
    // newKmsKeyId="K2" differs from current "K0" → KEY_ID_MISMATCH (stale skip).
    // Because oldKey == current.kmsKeyId, another handler will dispose it; skip orphan dispose.
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("K0", TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, "K0", "K2", ADDRESS, OPERATOR_ID, true));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K2"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("KEY_ID_MISMATCH"));
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
  }

  @Test
  void staleSkip_orphanDispose_idempotentSwallowsScheduleException() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("K2", TreasuryWalletStatus.ACTIVE)));
    doThrow(new RuntimeException("aws boom"))
        .when(kmsKeyLifecyclePort)
        .scheduleKeyDeletion("K0", 7);

    assertThatCode(
            () ->
                service.execute(
                    new ReplaceKmsKeyCommand(ALIAS, "K0", "K1", ADDRESS, OPERATOR_ID, true)))
        .doesNotThrowAnyException();

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K0"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_DISABLE),
            eq(true),
            eq("ORPHAN_FROM_STALE_REPLACE"));
    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K0"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_SCHEDULE_DELETION),
            eq(false),
            eq("ORPHAN_FROM_STALE_REPLACE:RuntimeException"));
  }

  @Test
  void staleSkip_disposeOldKeyFalse_skipsOrphanDispose() {
    // Stale state (KEY_ID_MISMATCH), but disposeOldKey=false → no orphan dispose.
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("K2", TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, "K0", "K1", ADDRESS, OPERATOR_ID, false));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K1"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("KEY_ID_MISMATCH"));
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
  }

  @Test
  void staleSkip_oldKmsKeyIdNull_skipsOrphanDispose() {
    // Stale state, disposeOldKey=true, but oldKmsKeyId=null → no orphan dispose.
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("K2", TreasuryWalletStatus.ACTIVE)));

    service.execute(new ReplaceKmsKeyCommand(ALIAS, null, "K1", ADDRESS, OPERATOR_ID, true));

    verify(kmsAuditRecorder)
        .record(
            eq(OPERATOR_ID),
            eq(ALIAS),
            eq("K1"),
            eq(ADDRESS),
            eq(KmsAuditAction.KMS_REPLACE_SKIPPED),
            eq(true),
            eq("KEY_ID_MISMATCH"));
    verify(kmsKeyLifecyclePort, never()).disableKey(anyString());
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(anyString(), anyInt());
    verify(kmsKeyLifecyclePort, never()).updateAlias(anyString(), anyString());
  }
}
